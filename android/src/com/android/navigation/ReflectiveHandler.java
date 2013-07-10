/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.navigation;

import com.android.annotations.Property;
import org.xml.sax.*;
import org.xml.sax.Locator;
import org.xml.sax.helpers.DefaultHandler;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

class ReflectiveHandler extends DefaultHandler {
  // public static final List<String> DEFAULT_PACKAGES = Arrays.<String>asList("java.lang", "android.view", "android.widget");
  public static final List<String> DEFAULT_PACKAGES = Arrays.asList();
  public static final List<String> DEFAULT_CLASSES = Arrays.asList();
  public static final String[] EMPTY_STRING_ARRAY = new String[0];

  private final List<String> packagesImports = new ArrayList<String>(DEFAULT_PACKAGES);
  private final List<String> classImports = new ArrayList<String>(DEFAULT_CLASSES);
  private final ErrorHandler errorHandler;
  private final Stack<ElementInfo> stack = new Stack<ElementInfo>();
  private final Map<String, Object> idToValue = new HashMap<String, Object>();
  private final Map<Class, Constructor> classToConstructor = new IdentityHashMap<Class, Constructor>();
  private final Map<Constructor, String[]> constructorToParameterNames = new IdentityHashMap<Constructor, String[]>();

  private Locator documentLocator;
  public Object result;

  public ReflectiveHandler(ErrorHandler errorHandler) {
    this.errorHandler = errorHandler;
  }

  @Override
  public void setDocumentLocator(Locator documentLocator) {
    this.documentLocator = documentLocator;
  }

  private void processNameSpace(String nameSpace) {
    String[] imports = nameSpace.split("[?;]");
    for (String imp : imports) {
      String importPrefix = "import=";
      String packageSuffix = ".*";
      if (imp.startsWith(importPrefix)) {
        if (imp.endsWith(packageSuffix)) {
          packagesImports.add(imp.substring(importPrefix.length(), imp.length() - packageSuffix.length()));
        }
        else {
          classImports.add(imp.substring(importPrefix.length()));
        }
      }
    }
  }

  public Class getClassForName(String tag) throws ClassNotFoundException {
    String simpleName = Utilities.capitalize(tag);
    ClassLoader classLoader = getClass().getClassLoader();
    for (String clazz : classImports) {
      if (clazz.endsWith("." + simpleName)) {
        return classLoader.loadClass(clazz);
      }
    }
    for (String pkg : packagesImports) {
      try {
        return classLoader.loadClass(pkg + "." + simpleName);
      }
      catch (ClassNotFoundException e) {
        // Class was not defined by this import, continue.
      }
    }
    throw new ClassNotFoundException("Could not find class for tag: " + tag);
  }

  private static class PropertyAnnotationNotFoundException extends Exception {
  }

  private static String getName(Annotation[] parameterAnnotation) throws PropertyAnnotationNotFoundException {
    for (Annotation a : parameterAnnotation) {
      if (a instanceof Property) {
        return ((Property)a).value();
      }
    }
    throw new PropertyAnnotationNotFoundException();
  }

  private static Object valueFor(Class<?> type, String stringValue)
    throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
    if (type == String.class) {
      return stringValue;
    }
    if (type.isPrimitive()) {
      type = Utilities.wrapperForPrimitiveType(type);
    }
    return type.getConstructor(String.class).newInstance(stringValue);
  }

  private static String[] findParameterNames(Constructor constructor) throws PropertyAnnotationNotFoundException {
    if (constructor == null) {
      return EMPTY_STRING_ARRAY;
    }
    Annotation[][] annotations = constructor.getParameterAnnotations();
    String[] result = new String[annotations.length];
    for (int i = 0; i < annotations.length; i++) {
      result[i] = getName(annotations[i]);
    }
    return result;
  }

  private static Constructor findConstructor(Class clz) {
    Constructor[] constructors = clz.getConstructors();
    Arrays.sort(constructors, new Comparator<Constructor>() {
      @Override
      public int compare(Constructor c1, Constructor c2) {
        return c1.getParameterTypes().length - c2.getParameterTypes().length;
      }
    });
    for (Constructor constructor : constructors) {
      if (!Modifier.isPublic(constructor.getModifiers())) {
        continue;
      }
      return constructor;
    }
    return null;
  }

  private Constructor getConstructor(Class clazz) {
    Constructor result = classToConstructor.get(clazz);
    if (result == null) {
      classToConstructor.put(clazz, result = findConstructor(clazz));
    }
    return result;
  }

  private String[] getParameterNames(Constructor constructor) {
    String[] result = constructorToParameterNames.get(constructor);
    if (result == null) {
      try {
        constructorToParameterNames.put(constructor, result = findParameterNames(constructor));
      }
      catch (PropertyAnnotationNotFoundException e) {
        throw new RuntimeException(e); // todo remove
      }
    }
    return result;
  }

  Object[] getParameterValues(Constructor constructor, Map<String, String> attributes, List<ElementInfo> elements)
    throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, SAXException {
    String[] parameterNames = getParameterNames(constructor);
    Class[] types = constructor.getParameterTypes();
    Object[] result = new Object[parameterNames.length];
    for (int i = 0; i < parameterNames.length; i++) {
      String parameterName = parameterNames[i];
      String stringValue = attributes.get(parameterName);
      if (stringValue != null) {
        result[i] = valueFor(types[i], stringValue);
      }
      else {
        ElementInfo param = getParam(elements, parameterName, constructor);
        param.myValueAlreadySetInOuter = true;
        result[i] = param.getValue();
      }
    }
    return result;
  }

  private static ElementInfo getParam(List<ElementInfo> elements, String parameterName, Object constructor) throws SAXException {
    for (ElementInfo element : elements) {
      if (parameterName.equals(element.name)) {
        return element;
      }
    }
    throw new SAXException("Unspecified parameter, " + parameterName + ", in " + constructor);
  }

  static abstract class Evaluator {
    abstract Object evaluate() throws SAXException;
  }

  static class LazyValue {
    private static Object UNSET = new Object();

    private Evaluator evaluator;
    private Object value = UNSET;

    Object getValue() throws SAXException {
      if (value == UNSET) {
        value = evaluator.evaluate();
      }
      return value;
    }

    void setEvaluator(Evaluator evaluator) {
      this.evaluator = evaluator;
    }

    void setValue(Object value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return "LazyValue{" +
             "value=" + (value == UNSET ? "<UNSET>" : value) +
             '}';
    }
  }

  static class ElementInfo {
    public Class type;
    public String name;
    public boolean myValueAlreadySetInOuter = false;
    public Map<String, String> attributes;
    public List<ElementInfo> elements = new ArrayList<ElementInfo>();
    private LazyValue lazyValue = new LazyValue();


    public Object getValue() throws SAXException {
      return lazyValue.getValue();
    }

    public void setValue(Object value) {
      lazyValue.setValue(value);
    }

    public void setEvaluator(Evaluator evaluator) {
      lazyValue.setEvaluator(evaluator);
    }
  }

  private static void applyMethod(Object target, String methodName, Object parameter)
    throws InvocationTargetException, IllegalAccessException {
    Class targetType = target.getClass();
    Class parameterType = parameter.getClass();
    for (Method m : targetType.getMethods()) {
      if (m.getName().equals(methodName)) {
        Class<?>[] parameterTypes = m.getParameterTypes();
        if (parameterTypes.length == 1 && parameterTypes[0].isAssignableFrom(parameterType)) {
          m.invoke(target, parameter);
          break;
        }
      }
    }
  }

  private void handleWarning(Exception e) throws SAXException {
    errorHandler.warning(new SAXParseException(e.getMessage(), documentLocator, e));
  }

  private void handleError(Exception e) throws SAXException {
    errorHandler.error(new SAXParseException(e.getMessage(), documentLocator, e));
  }

  private static Method getGetter(Class<?> type, String propertyName) throws NoSuchMethodException {
    String getterMethodName = Utilities.getGetterMethodName(propertyName);
    return type.getMethod(getterMethodName);
  }

  private static Method getSetter(Class<?> type, String propertyName) throws NoSuchMethodException {
    String setterMethodName = Utilities.getSetterMethodName(propertyName);
    Class propertyType = getGetter(type, propertyName).getReturnType();
    return type.getMethod(setterMethodName, propertyType);
  }

  private String[] getConstructorParameterNames(Class type) {
    if (type == null) {
      return EMPTY_STRING_ARRAY;
    }
    return getParameterNames(getConstructor(type));
  }

  private void setProperties(ElementInfo elementInfo) throws SAXException {
    String[] constructorParameterNames = getConstructorParameterNames(elementInfo.type);
    for (Map.Entry<String, String> entry : elementInfo.attributes.entrySet()) {
      String attributeName = entry.getKey();
      if (Utilities.RESERVED_ATTRIBUTES.contains(attributeName) || Utilities.contains(constructorParameterNames, attributeName)) {
        continue;
      }
      try {
        Method setter = getSetter(elementInfo.type, attributeName);
        Class argType = Utilities.wrapperForPrimitiveType(setter.getParameterTypes()[0]);
        setter.invoke(elementInfo.getValue(), valueFor(argType, entry.getValue()));
      }
      catch (NoSuchMethodException e) {
        handleWarning(e);
      }
      catch (IllegalAccessException e) {
        handleWarning(e);
      }
      catch (InvocationTargetException e) {
        handleWarning(e);
      }
      catch (InstantiationException e) {
        handleWarning(e);
      }
    }
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    final ElementInfo elementInfo = new ElementInfo();
    elementInfo.name = qName;
    elementInfo.attributes = Utilities.toMap(attributes);
    String nameSpace = elementInfo.attributes.get(Utilities.NAME_SPACE_ATRIBUTE_NAME);
    if (nameSpace != null) {
      processNameSpace(nameSpace);
    }
    try {
      String idref = elementInfo.attributes.get(Utilities.IDREF_ATTRIBUTE_NAME);
      if (idref != null) {
        if (!idToValue.containsKey(idref)) {
          throw new SAXException("IDREF attribute, \"" + idref + "\" , was used before corresponding ID was defined.");
        }
        elementInfo.setValue(idToValue.get(idref));
      }
      else {
        elementInfo.type = getType(qName, elementInfo.attributes.get("class"));
        if (elementInfo.type != null) {
          elementInfo.setEvaluator(new Evaluator() {
            @Override
            Object evaluate() throws SAXException {
              try {
                Constructor constructor = getConstructor(elementInfo.type);
                // note info.elements is changing under our feet
                return constructor.newInstance(getParameterValues(constructor, elementInfo.attributes, elementInfo.elements));
              }
              catch (IllegalAccessException e) {
                throw new SAXException(e);
              }
              catch (NoSuchMethodException e) {
                throw new SAXException(e);
              }
              catch (InvocationTargetException e) {
                throw new SAXException(e);
              }
              catch (InstantiationException e) {
                throw new RuntimeException(e);
              }
            }
          });
        }
        else {
          final ElementInfo last = stack.getLast();
          final Method getter = getGetter(last.type, qName);
          elementInfo.myValueAlreadySetInOuter = true;
          elementInfo.type = getter.getReturnType();
          elementInfo.setEvaluator(new Evaluator() {
            @Override
            Object evaluate() throws SAXException {
              try {
                return getter.invoke(last.getValue());
              }
              catch (IllegalAccessException e) {
                throw new SAXException(e);
              }
              catch (InvocationTargetException e) {
                throw new SAXException(e);
              }
            }
          });
        }
      }
      String id = elementInfo.attributes.get(Utilities.ID_ATTRIBUTE_NAME);
      if (id != null) {
        idToValue.put(id, elementInfo.getValue());
      }
      stack.push(elementInfo);
    }
    catch (ClassNotFoundException e) {
      handleError(e);
    }
    catch (NoSuchMethodException e) {
      handleError(e);
    }
  }

  private Class getType(String qName, String className) throws ClassNotFoundException {
    if (className != null) {
      return getClass().getClassLoader().loadClass(className);
    }
    else {
      try {
        return getClassForName(qName);
      }
      catch (ClassNotFoundException e) {
        Class outerType = stack.getLast().type;
        try {
          Method setter = getSetter(outerType, qName);
          return setter.getParameterTypes()[0];
        }
        catch (NoSuchMethodException e1) {
          Constructor constructor = getConstructor(outerType);
          String[] parameterNames = getParameterNames(constructor);
          Class[] parameterTypes = constructor.getParameterTypes();
          for (int i = 0; i < parameterNames.length; i++) {
            if (parameterNames[i].equals(qName)) {
              return parameterTypes[i];
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  public void characters(char[] ch, int start, int length) throws SAXException {
  }

  @Override
  public void endElement(String uri, String localName, String qName) throws SAXException {
    ElementInfo elementInfo = stack.pop();
    try {
      result = elementInfo.getValue();
      setProperties(elementInfo);
      for (ElementInfo element : elementInfo.elements) {
        if (element.myValueAlreadySetInOuter) {
          continue;
        }
        Object outerValue = elementInfo.getValue();
        if (!(Collection.class.isAssignableFrom(elementInfo.type))) { // todo remove Collection check
          getSetter(outerValue.getClass(), element.name).invoke(outerValue, element.getValue());
        }
        else {
          applyMethod(outerValue, "add", element.getValue());
        }
      }
      if (stack.size() != 0) {
        stack.getLast().elements.add(elementInfo);
      }
    }
    catch (NoSuchMethodException e) {
      handleError(e);
    }
    catch (IllegalAccessException e) {
      handleError(e);
    }
    catch (InvocationTargetException e) {
      handleError(e);
    }
  }
}
