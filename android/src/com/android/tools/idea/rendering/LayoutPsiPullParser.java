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
package com.android.tools.idea.rendering;

import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.res2.ValueXmlHelper;
import com.android.resources.Density;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.xmlpull.v1.XmlPullParserException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.rendering.RenderService.AttributeFilter;

/**
 * {@link com.android.ide.common.rendering.api.ILayoutPullParser} implementation on top of
 * the PSI {@link XmlTag}.
 * <p/>
 * It's designed to work on layout files, and will not work on other resource files (no text event
 * support for example).
 * <p/>
 * This pull parser generates {@link com.android.ide.common.rendering.api.ViewInfo}s whose keys
 * are of type {@link XmlTag}.
 */
public class LayoutPsiPullParser extends LayoutPullParser {
  @NotNull
  private final RenderLogger myLogger;

  @NotNull
  private final List<Element> myNodeStack = new ArrayList<Element>();

  @Nullable
  protected final Element myRoot;

  @Nullable
  private String myToolsPrefix;

  @Nullable
  protected String myAndroidPrefix;

  private boolean myProvideViewCookies = true;

  /**
   * Constructs a new {@link LayoutPsiPullParser}, a parser dedicated to the special case of
   * parsing a layout resource files.
   *
   * @param file         The {@link XmlTag} for the root node.
   * @param logger       The logger to emit warnings too, such as missing fragment associations
   */
  @NotNull
  public static LayoutPsiPullParser create(@NotNull XmlFile file, @NotNull RenderLogger logger) {
    return new LayoutPsiPullParser(file, logger);
  }

  /**
   * Constructs a new {@link LayoutPsiPullParser}, a parser dedicated to the special case of
   * parsing a layout resource files, and handling "exploded rendering" - adding padding on views
   * to make them easier to see and operate on.
   *
   * @param file         The {@link com.intellij.psi.xml.XmlTag} for the root node.
   * @param logger       The logger to emit warnings too, such as missing fragment associations
   * @param explodeNodes A set of individual nodes that should be assigned a fixed amount of
 *                       padding ({@link com.android.tools.idea.rendering.PaddingLayoutPsiPullParser#FIXED_PADDING_VALUE}).
 *                       This is intended for use with nodes that (without padding) would be
 *                       invisible.
   * @param density      the density factor for the screen.
   */
  @NotNull
  public static LayoutPsiPullParser create(@NotNull XmlFile file,
                                           @NotNull RenderLogger logger,
                                           @Nullable Set<XmlTag> explodeNodes,
                                           @NotNull Density density) {
    if (explodeNodes != null && !explodeNodes.isEmpty()) {
      return new PaddingLayoutPsiPullParser(file, logger, explodeNodes, density);
    } else {
      return new LayoutPsiPullParser(file, logger);
    }
  }

  @NotNull
  public static LayoutPsiPullParser create(@Nullable final AttributeFilter filter,
                                           @NotNull XmlTag root,
                                           @NotNull RenderLogger logger) {
    return new LayoutPsiPullParser(root, logger) {
      @Override
      public String getAttributeValue(final String namespace, final String localName) {
        if (filter != null) {
          Element element = getCurrentNode();
          if (element != null) {
            final XmlTag tag = element.cookie;
            String value;
            if (ApplicationManager.getApplication().isReadAccessAllowed()) {
              value = filter.getAttribute(tag, namespace, localName);
            } else {
              value = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
                @Override
                @Nullable
                public String compute() {
                  return filter.getAttribute(tag, namespace, localName);
                }
              });
            }
            if (value != null) {
              if (value.isEmpty()) { // empty means unset
                return null;
              }
              return value;
            }
            // null means no preference, not "unset".
          }
        }

        return super.getAttributeValue(namespace, localName);
      }
    };
  }

  /** Use one of the {@link #create} factory methods instead */
  protected LayoutPsiPullParser(@NotNull XmlFile file, @NotNull RenderLogger logger) {
    this(file.getRootTag(), logger);
  }

  protected LayoutPsiPullParser(@Nullable final XmlTag root, @NotNull RenderLogger logger) {
    myLogger = logger;

    if (root != null) {
      if (ApplicationManager.getApplication().isReadAccessAllowed()) {
        myAndroidPrefix = root.getPrefixByNamespace(ANDROID_URI);
        myToolsPrefix = root.getPrefixByNamespace(TOOLS_URI);
        myRoot = createSnapshot(root);
      } else {
        myRoot = ApplicationManager.getApplication().runReadAction(new Computable<Element>() {

          @Override
          public Element compute() {
            myAndroidPrefix = root.getPrefixByNamespace(ANDROID_URI);
            myToolsPrefix = root.getPrefixByNamespace(TOOLS_URI);
            return createSnapshot(root);
          }
        });
      }
    } else {
      myRoot = null;
    }
  }

  @Nullable
  protected final Element getCurrentNode() {
    if (myNodeStack.size() > 0) {
      return myNodeStack.get(myNodeStack.size() - 1);
    }

    return null;
  }

  @Nullable
  protected final Attribute getAttribute(int i) {
    if (myParsingState != START_TAG) {
      throw new IndexOutOfBoundsException();
    }

    // get the current uiNode
    Element uiNode = getCurrentNode();
    if (uiNode != null) {
      return uiNode.attributes.get(i);
    }

    return null;
  }

  protected void push(@NotNull Element node) {
    myNodeStack.add(node);
  }

  @NotNull
  protected Element pop() {
    return myNodeStack.remove(myNodeStack.size() - 1);
  }

  // ------------- IXmlPullParser --------

  /**
   * {@inheritDoc}
   * <p/>
   * This implementation returns the underlying DOM node of type {@link XmlTag}.
   * Note that the link between the GLE and the parsing code depends on this being the actual
   * type returned, so you can't just randomly change it here.
   */
  @Nullable
  @Override
  public Object getViewCookie() {
    if (myProvideViewCookies) {
      Element element = getCurrentNode();
      if (element != null) {
        return element.cookie;
      }
    }

    return null;
  }

  /**
   * Legacy method required by {@link com.android.layoutlib.api.IXmlPullParser}
   */
  @SuppressWarnings("deprecation")
  @Nullable
  @Override
  public Object getViewKey() {
    return getViewCookie();
  }

  /**
   * This implementation does nothing for now as all the embedded XML will use a normal KXML
   * parser.
   */
  @Nullable
  @Override
  public ILayoutPullParser getParser(String layoutName) {
    return null;
  }

  // ------------- XmlPullParser --------

  @Override
  public String getPositionDescription() {
    return "XML DOM element depth:" + myNodeStack.size();
  }

  /*
   * This does not seem to be called by the layoutlib, but we keep this (and maintain
   * it) just in case.
   */
  @Override
  public int getAttributeCount() {
    Element node = getCurrentNode();

    if (node != null) {
      return node.attributes.size();
    }

    return 0;
  }

  /*
   * This does not seem to be called by the layoutlib, but we keep this (and maintain
   * it) just in case.
   */
  @Nullable
  @Override
  public String getAttributeName(int i) {
    Attribute attribute = getAttribute(i);
    if (attribute != null) {
      return attribute.name;
    }

    return null;
  }

  /*
   * This does not seem to be called by the layoutlib, but we keep this (and maintain
   * it) just in case.
   */
  @Override
  public String getAttributeNamespace(int i) {
    Attribute attribute = getAttribute(i);
    if (attribute != null) {
      return attribute.namespace;
    }
    return ""; //$NON-NLS-1$
  }

  /*
   * This does not seem to be called by the layoutlib, but we keep this (and maintain
   * it) just in case.
   */
  @Nullable
  @Override
  public String getAttributePrefix(int i) {
    Attribute attribute = getAttribute(i);
    if (attribute != null) {
      String prefix = attribute.prefix;
      if (prefix.isEmpty()) {
        prefix = null;
      }
      return prefix;
    }
    return null;
  }

  /*
   * This does not seem to be called by the layoutlib, but we keep this (and maintain
   * it) just in case.
   */
  @Nullable
  @Override
  public String getAttributeValue(int i) {
    Attribute attribute = getAttribute(i);
    if (attribute != null) {
      return attribute.value;
    }

    return null;
  }

  /*
   * This is the main method used by the LayoutInflater to query for attributes.
   */
  @Nullable
  @Override
  public String getAttributeValue(String namespace, String localName) {
    // get the current uiNode
    Element tag = getCurrentNode();
    if (tag != null) {
      if (ATTR_LAYOUT.equals(localName) && VIEW_FRAGMENT.equals(tag.tag)) {
        String layout = tag.getAttribute(LayoutMetadata.KEY_FRAGMENT_LAYOUT, TOOLS_URI);
        if (layout != null) {
          return layout;
        }
      }

      String value = null;
      if (namespace == null) {
        value = tag.getAttribute(localName);
      } else if (namespace.equals(ANDROID_URI)) {
        if (myAndroidPrefix != null) {
          if (myToolsPrefix != null) {
            for (Attribute attribute : tag.attributes) {
              if (localName.equals(attribute.name)) {
                if (myToolsPrefix.equals(attribute.prefix)) {
                  value = attribute.value;
                  if (value.isEmpty()) {
                    // Empty when there is a runtime attribute set means unset the runtime attribute
                    value = tag.getAttribute(localName, ANDROID_URI) != null ? null : value;
                  }
                  break;
                } else if (myAndroidPrefix.equals(attribute.prefix)) {
                  value = attribute.value;
                  // Don't break: continue searching in case we find a tools design time attribute
                }
              }
            }
          } else {
            value = tag.getAttribute(localName, ANDROID_URI);
          }
        } else {
          value = tag.getAttribute(localName, namespace);
        }
      } else {
        // Auto-convert http://schemas.android.com/apk/res-auto resources. The lookup
        // will be for the current application's resource package, e.g.
        // http://schemas.android.com/apk/res/foo.bar, but the XML document will
        // be using http://schemas.android.com/apk/res-auto in library projects:
        for (Attribute attribute : tag.attributes) {
          if (localName.equals(attribute.name) && (namespace.equals(attribute.namespace) ||
                                                   AUTO_URI.equals(attribute.namespace))) {
            value = attribute.value;
            break;
          }
        }
      }

      if (value != null) {
        // on the fly convert match_parent to fill_parent for compatibility with older
        // platforms.
        if (VALUE_MATCH_PARENT.equals(value) &&
            (ATTR_LAYOUT_WIDTH.equals(localName) || ATTR_LAYOUT_HEIGHT.equals(localName)) &&
            ANDROID_URI.equals(namespace)) {
          return VALUE_FILL_PARENT;
        }

        // Handle unicode and XML escapes
        for (int i = 0, n = value.length(); i < n; i++) {
          char c = value.charAt(i);
          if (c == '&' || c == '\\') {
            value = ValueXmlHelper.unescapeResourceString(value, true, false);
            break;
          }
        }
      }

      return value;
    }

    return null;
  }

  @Override
  public int getDepth() {
    return myNodeStack.size();
  }

  @Nullable
  @Override
  public String getName() {
    if (myParsingState == START_TAG || myParsingState == END_TAG) {
      Element currentNode = getCurrentNode();
      assert currentNode != null; // Should only be called when START_TAG
      String name = currentNode.tag;
      if (name.equals(VIEW_FRAGMENT)) {
        // Temporarily translate <fragment> to <include> (and in getAttribute
        // we will also provide a layout-attribute for the corresponding
        // fragment name attribute)
        String layout = currentNode.getAttribute(LayoutMetadata.KEY_FRAGMENT_LAYOUT, TOOLS_URI);
        if (layout != null) {
          return VIEW_INCLUDE;
        } else {
          String fragmentId = currentNode.getAttribute(ATTR_CLASS);
          if (fragmentId == null || fragmentId.isEmpty()) {
            fragmentId = currentNode.getAttribute(ATTR_NAME, ANDROID_URI);
            if (fragmentId == null || fragmentId.isEmpty()) {
              fragmentId = currentNode.getAttribute(ATTR_ID, ANDROID_URI);
            }
          }
          myLogger.warning(RenderLogger.TAG_MISSING_FRAGMENT, "Missing fragment association", fragmentId);
        }
      }

      return name;
    }

    return null;
  }

  @Nullable
  @Override
  public String getNamespace() {
    if (myParsingState == START_TAG || myParsingState == END_TAG) {
      Element currentNode = getCurrentNode();
      assert currentNode != null;  // Should only be called when START_TAG
      return currentNode.namespace;
    }

    return null;
  }

  @Nullable
  @Override
  public String getPrefix() {
    if (myParsingState == START_TAG || myParsingState == END_TAG) {
      Element currentNode = getCurrentNode();
      assert currentNode != null;  // Should only be called when START_TAG
      String prefix = currentNode.prefix;
      if (prefix.isEmpty()) {
        prefix = null;
      }
      return prefix;
    }

    return null;
  }

  @Override
  public boolean isEmptyElementTag() throws XmlPullParserException {
    if (myParsingState == START_TAG) {
      Element currentNode = getCurrentNode();
      assert currentNode != null;  // Should only be called when START_TAG
      // This isn't quite right; if layoutlib starts needing this, stash XmlTag#isEmpty() in snapshot
      return currentNode.children.isEmpty();
    }

    throw new XmlPullParserException("Call to isEmptyElementTag while not in START_TAG", this, null);
  }

  @Override
  protected void onNextFromStartDocument() {
    if (myRoot != null) {
      push(myRoot);
      myParsingState = START_TAG;
    } else {
      myParsingState = END_DOCUMENT;
    }
  }

  @Override
  protected void onNextFromStartTag() {
    // get the current node, and look for text or children (children first)
    Element node = getCurrentNode();
    assert node != null;  // Should only be called when START_TAG
    List<Element> children = node.children;
    if (!children.isEmpty()) {
      // move to the new child, and don't change the state.
      push(children.get(0));

      // in case the current state is CURRENT_DOC, we set the proper state.
      myParsingState = START_TAG;
    }
    else {
      if (myParsingState == START_DOCUMENT) {
        // this handles the case where there's no node.
        myParsingState = END_DOCUMENT;
      }
      else {
        myParsingState = END_TAG;
      }
    }
  }

  @Override
  protected void onNextFromEndTag() {
    // look for a sibling. if no sibling, go back to the parent
    Element node = getCurrentNode();
    assert node != null;  // Should only be called when END_TAG

    Element sibling = node.next;
    if (sibling != null) {
      node = sibling;
      // to go to the sibling, we need to remove the current node,
      pop();
      // and add its sibling.
      push(node);
      myParsingState = START_TAG;
    }
    else {
      // move back to the parent
      pop();

      // we have only one element left (myRoot), then we're done with the document.
      if (myNodeStack.isEmpty()) {
        myParsingState = END_DOCUMENT;
      }
      else {
        myParsingState = END_TAG;
      }
    }
  }

  /** Sets whether this parser will provide view cookies */
  public void setProvideViewCookies(boolean provideViewCookies) {
    myProvideViewCookies = provideViewCookies;
  }

  private static Element createSnapshot(XmlTag tag) {
    Element element = new Element(tag);

    // Attributes
    XmlAttribute[] psiAttributes = tag.getAttributes();
    List<Attribute> attributes = Lists.newArrayListWithExpectedSize(psiAttributes.length);
    element.attributes = attributes;
    for (XmlAttribute psiAttribute : psiAttributes) {
      Attribute attribute = createSnapshot(psiAttribute);
      attributes.add(attribute);
    }

    // Children
    XmlTag[] subTags = tag.getSubTags();
    if (subTags.length > 0) {
      Element last = null;
      ArrayList<Element> children = Lists.newArrayListWithExpectedSize(subTags.length);
      element.children = children;
      for (XmlTag subTag : subTags) {
        Element child = createSnapshot(subTag);
        children.add(child);
        if (last != null) {
          last.next = child;
        }
        last = child;
      }
    } else {
      element.children = Collections.emptyList();
    }

    return element;
  }

  private static Attribute createSnapshot(XmlAttribute psiAttribute) {
    String localName = psiAttribute.getLocalName();
    String namespace = psiAttribute.getNamespace();
    String prefix = psiAttribute.getNamespacePrefix();
    String value = psiAttribute.getValue();
    return new Attribute(namespace, prefix, localName, value);
  }

  protected static class Element {
    public final String namespace;
    public final String tag;
    public final XmlTag cookie;
    private final String prefix;
    public Element next;
    public List<Element> children;
    public List<Attribute> attributes;

    public Element(XmlTag tag) {
      this.tag = tag.getName();
      this.prefix = tag.getNamespacePrefix();
      this.namespace = tag.getNamespace();
      this.cookie = tag;
    }

    @Nullable
    public String getAttribute(String name) {
      return getAttribute(name, null);
    }

    @Nullable
    public String getAttribute(String name, @Nullable String namespace) {
      // We just use a list rather than a map since in layouts the number of attributes is
      // typically very small so map overhead isn't worthwhile
      for (Attribute attribute : attributes) {
        if (name.equals(attribute.name) && (namespace == null || namespace.equals(attribute.namespace))) {
          return attribute.value;
        }
      }

      return null;
    }
  }

  protected static class Attribute {
    public String namespace;
    public String prefix;
    public String name;
    public String value;

    private Attribute(String namespace, String prefix, String name, @Nullable String value) {
      this.namespace = namespace;
      this.prefix = prefix;
      this.name = name;
      this.value = value;
    }
  }
}
