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

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("SpellCheckingInspection")
public class RenderErrorPanelTest extends AndroidTestCase {
  public static final String BASE_PATH = "render/";

  @Override
  protected boolean requireRecentSdk() {
    // Need valid layoutlib install
    return true;
  }

  private String getRenderOutput(VirtualFile file) {
    assertNotNull(file);
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    assertNotNull(facet);
    ConfigurationManager configurationManager = facet.getConfigurationManager();
    assertNotNull(configurationManager);
    Configuration configuration = configurationManager.getConfiguration(file);
    RenderLogger logger = new RenderLogger("myLogger", myModule);
    RenderService service = RenderService.create(facet, myModule, psiFile, configuration, logger, null);
    assertNotNull(service);
    RenderResult render = service.render();
    assertNotNull(render);
    assertTrue(logger.hasProblems());
    RenderErrorPanel panel = new RenderErrorPanel();
    String html = panel.showErrors(render);
    assert html != null;
    html = stripImages(html);
    return html;
  }

  public void testPanel() {
    String html = getRenderOutput(myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml"));
    assertEquals(
      "<html><body><A HREF=\"action:close\"></A><font style=\"font-weight:bold; color:#005555;\">Rendering Problems</font><BR/>\n" +
      "<B>NOTE: One or more layouts are missing the layout_width or layout_height attributes. These are required in most layouts.</B><BR/>\n" +
      "&lt;LinearLayout> does not set the required layout_width attribute: <BR/>\n" +
      "&nbsp;&nbsp;&nbsp;&nbsp;<A HREF=\"command:0\">Set to wrap_content</A>, <A HREF=\"command:1\">Set to match_parent</A><BR/>\n" +
      "&lt;LinearLayout> does not set the required layout_height attribute: <BR/>\n" +
      "&nbsp;&nbsp;&nbsp;&nbsp;<A HREF=\"command:2\">Set to wrap_content</A>, <A HREF=\"command:3\">Set to match_parent</A><BR/>\n" +
      "<BR/>\n" +
      "Or: <A HREF=\"command:4\">Automatically add all missing attributes</A><BR/>\n" +
      "<BR/>\n" +
      "<BR/>\n" +
      "The following classes could not be found:<DL>\n" +
      "<DD>-&NBSP;LinerLayout (<A HREF=\"action:classpath\">Fix Build Path</A>)\n" +
      "</DL>Tip: Try to <A HREF=\"action:build\">build</A> the project.<BR/>\n" +
      "<BR/>\n" +
      "</body></html>",
     html);
  }

  public void testTypo() {
    String html = getRenderOutput(myFixture.copyFileToProject(BASE_PATH + "layout3.xml", "res/layout/layout3.xml"));
    assertEquals(
      "<html><body><A HREF=\"action:close\"></A><font style=\"font-weight:bold; color:#005555;\">Rendering Problems</font><BR/>\n" +
      "The following classes could not be found:<DL>\n" +
      "<DD>-&NBSP;Bitton (<A HREF=\"action:classpath\">Fix Build Path</A>)\n" +
      "</DL>Tip: Try to <A HREF=\"action:build\">build</A> the project.<BR/>\n" +
      "<BR/>\n" +
      "</body></html>",
      html);
  }

  public void testBrokenLayoutLib() {
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml");
    assertNotNull(file);
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    assertNotNull(facet);
    ConfigurationManager configurationManager = facet.getConfigurationManager();
    assertNotNull(configurationManager);
    Configuration configuration = configurationManager.getConfiguration(file);
    RenderLogger logger = new RenderLogger("mylogger", myModule);
    RenderService service = RenderService.create(facet, myModule, psiFile, configuration, logger, null);
    assertNotNull(service);
    RenderResult render = service.render();
    assertNotNull(render);

    // MANUALLY register errors
    logger.error(null, "This is an error with entities: & < \"", null);

    Throwable throwable = createExceptionFromDesc(
      "java.lang.NullPointerException\n" +
      "\tat android.text.format.DateUtils.getDayOfWeekString(DateUtils.java:248)\n" +
      "\tat android.widget.CalendarView.setUpHeader(CalendarView.java:1034)\n" +
      "\tat android.widget.CalendarView.<init>(CalendarView.java:403)\n" +
      "\tat android.widget.CalendarView.<init>(CalendarView.java:333)\n" +
      "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)\n" +
      "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:39)\n" +
      "\tat sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:27)\n" +
      "\tat java.lang.reflect.Constructor.newInstance(Constructor.java:513)\n" +
      "\tat android.view.LayoutInflater.createView(LayoutInflater.java:594)\n" +
      "\tat android.view.BridgeInflater.onCreateView(BridgeInflater.java:86)\n" +
      "\tat android.view.LayoutInflater.onCreateView(LayoutInflater.java:669)\n" +
      "\tat android.view.LayoutInflater.createViewFromTag(LayoutInflater.java:694)\n" +
      "\tat android.view.BridgeInflater.createViewFromTag(BridgeInflater.java:131)\n" +
      "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:755)\n" +
      "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)\n" +
      "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:727)\n" +
      "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:492)\n" +
      "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:397)\n" +
      "\tat android.widget.DatePicker.<init>(DatePicker.java:171)\n" +
      "\tat android.widget.DatePicker.<init>(DatePicker.java:145)\n" +
      "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)\n" +
      "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:39)\n" +
      "\tat sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:27)\n" +
      "\tat java.lang.reflect.Constructor.newInstance(Constructor.java:513)\n" +
      "\tat android.view.LayoutInflater.createView(LayoutInflater.java:594)\n" +
      "\tat android.view.BridgeInflater.onCreateView(BridgeInflater.java:86)\n" +
      "\tat android.view.LayoutInflater.onCreateView(LayoutInflater.java:669)\n" +
      "\tat android.view.LayoutInflater.createViewFromTag(LayoutInflater.java:694)\n" +
      "\tat android.view.BridgeInflater.createViewFromTag(BridgeInflater.java:131)\n" +
      "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:755)\n" +
      "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)\n" +
      "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:727)\n" +
      "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:492)\n" +
      "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:373)\n" +
      "\tat com.android.layoutlib.bridge.impl.RenderSessionImpl.inflate(RenderSessionImpl.java:385)\n" +
      "\tat com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:332)\n" +
      "\tat com.android.ide.common.rendering.LayoutLibrary.createSession(LayoutLibrary.java:325)\n" +
      "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:525)\n" +
      "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:518)\n" +
      "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:958)\n" +
      "\tat com.android.tools.idea.rendering.RenderService.createRenderSession(RenderService.java:518)\n" +
      "\tat com.android.tools.idea.rendering.RenderService.render(RenderService.java:555)\n" +
      "\tat com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel$7$2.compute(AndroidDesignerEditorPanel.java:498)\n" +
      "\tat com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel$7$2.compute(AndroidDesignerEditorPanel.java:491)\n" +
      "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:969)\n" +
      "\tat com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel$7.run(AndroidDesignerEditorPanel.java:491)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:320)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:310)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue$2.run(MergingUpdateQueue.java:254)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:269)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:227)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue.run(MergingUpdateQueue.java:217)\n" +
      "\tat com.intellij.util.concurrency.QueueProcessor.runSafely(QueueProcessor.java:237)\n" +
      "\tat com.intellij.util.Alarm$Request$1.run(Alarm.java:297)\n" +
      "\tat java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:439)\n" +
      "\tat java.util.concurrent.FutureTask$Sync.innerRun(FutureTask.java:303)\n" +
      "\tat java.util.concurrent.FutureTask.run(FutureTask.java:138)\n" +
      "\tat java.util.concurrent.ThreadPoolExecutor$Worker.runTask(ThreadPoolExecutor.java:895)\n" +
      "\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:918)\n" +
      "\tat java.lang.Thread.run(Thread.java:680)\n");
    logger.error(null, null, throwable, null);

    assertTrue(logger.hasProblems());
    RenderErrorPanel panel = new RenderErrorPanel();
    String html = panel.showErrors(render);
    assert html != null;
    html = stripImages(html);

    assertEquals(
      "<html><body><A HREF=\"action:close\"></A><font style=\"font-weight:bold; color:#005555;\">Rendering Problems</font><BR/>\n" +
      "This is an error with entities: &amp; &lt; \"<BR/>\n" +
      "&lt;CalendarView> and &lt;DatePicker> are broken in this version of the rendering library. " +
      "Try updating your SDK in the SDK Manager when issue 59732 is fixed. " +
      "(<A HREF=\"http://b.android.com/59732\">Open Issue 59732</A>, <A HREF=\"runnable:0\">Show Exception</A>)<BR/>\n" +
      "</body></html>",
      html);
  }

  public void testBrokenCustomView() {
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml");
    assertNotNull(file);
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    assertNotNull(facet);
    ConfigurationManager configurationManager = facet.getConfigurationManager();
    assertNotNull(configurationManager);
    Configuration configuration = configurationManager.getConfiguration(file);
    RenderLogger logger = new RenderLogger("mylogger", myModule);
    RenderService service = RenderService.create(facet, myModule, psiFile, configuration, logger, null);
    assertNotNull(service);
    RenderResult render = service.render();
    assertNotNull(render);

    Throwable throwable = createExceptionFromDesc(
      "java.lang.ArithmeticException: / by zero\n" +
      "\tat com.example.myapplication574.MyCustomView.<init>(MyCustomView.java:13)\n" +
      "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)\n" +
      "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:39)\n" +
      "\tat sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:27)\n" +
      "\tat java.lang.reflect.Constructor.newInstance(Constructor.java:513)\n" +
      "\tat org.jetbrains.android.uipreview.ViewLoader.createNewInstance(ViewLoader.java:365)\n" +
      "\tat org.jetbrains.android.uipreview.ViewLoader.loadView(ViewLoader.java:97)\n" +
      "\tat com.android.tools.idea.rendering.ProjectCallback.loadView(ProjectCallback.java:121)\n" +
      "\tat android.view.BridgeInflater.loadCustomView(BridgeInflater.java:207)\n" +
      "\tat android.view.BridgeInflater.createViewFromTag(BridgeInflater.java:135)\n" +
      "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:755)\n" +
      "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)\n" +
      "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:727)\n" +
      "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:492)\n" +
      "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:373)\n" +
      "\tat com.android.layoutlib.bridge.impl.RenderSessionImpl.inflate(RenderSessionImpl.java:385)\n" +
      "\tat com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:332)\n" +
      "\tat com.android.ide.common.rendering.LayoutLibrary.createSession(LayoutLibrary.java:325)\n" +
      "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:525)\n" +
      "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:518)\n" +
      "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:958)\n" +
      "\tat com.android.tools.idea.rendering.RenderService.createRenderSession(RenderService.java:518)\n" +
      "\tat com.android.tools.idea.rendering.RenderService.render(RenderService.java:555)\n" +
      "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$9.compute(AndroidLayoutPreviewToolWindowManager.java:418)\n" +
      "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$9.compute(AndroidLayoutPreviewToolWindowManager.java:411)\n" +
      "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:969)\n" +
      "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager.doRender(AndroidLayoutPreviewToolWindowManager.java:411)\n" +
      "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager.access$1100(AndroidLayoutPreviewToolWindowManager.java:79)\n" +
      "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$8$1.run(AndroidLayoutPreviewToolWindowManager.java:373)\n" +
      "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl$2.run(ProgressManagerImpl.java:178)\n" +
      "\tat com.intellij.openapi.progress.ProgressManager.executeProcessUnderProgress(ProgressManager.java:207)\n" +
      "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl.executeProcessUnderProgress(ProgressManagerImpl.java:212)\n" +
      "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl.runProcess(ProgressManagerImpl.java:171)\n" +
      "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$8.run(AndroidLayoutPreviewToolWindowManager.java:368)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:320)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:310)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue$2.run(MergingUpdateQueue.java:254)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:269)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:227)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue.run(MergingUpdateQueue.java:217)\n" +
      "\tat com.intellij.util.concurrency.QueueProcessor.runSafely(QueueProcessor.java:237)\n" +
      "\tat com.intellij.util.Alarm$Request$1.run(Alarm.java:297)\n" +
      "\tat java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:439)\n" +
      "\tat java.util.concurrent.FutureTask$Sync.innerRun(FutureTask.java:303)\n" +
      "\tat java.util.concurrent.FutureTask.run(FutureTask.java:138)\n" +
      "\tat java.util.concurrent.ThreadPoolExecutor$Worker.runTask(ThreadPoolExecutor.java:895)\n" +
      "\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:918)\n" +
      "\tat java.lang.Thread.run(Thread.java:680)\n");
    logger.error(null, null, throwable, null);

    assertTrue(logger.hasProblems());
    RenderErrorPanel panel = new RenderErrorPanel();
    String html = panel.showErrors(render);
    assert html != null;
    html = stripImages(html);
    html = stripSdkHome(html);

    assertNotNull(configuration);
    boolean havePlatformSources = RenderErrorPanel.findPlatformSources(configuration.getTarget()) != null;
    if (havePlatformSources) {
      assertEquals(
        "<html><body><A HREF=\"action:close\"></A><font style=\"font-weight:bold; color:#005555;\">Rendering Problems</font><BR/>\n" +
        "java.lang.ArithmeticException: / by zero<BR/>\n" +
        "&nbsp;&nbsp;at com.example.myapplication574.MyCustomView.&lt;init>(<A HREF=\"open:com.example.myapplication574.MyCustomView#<init>;MyCustomView.java:13\">MyCustomView.java:13</A>)<BR/>\n" +
        "&nbsp;&nbsp;at java.lang.reflect.Constructor.newInstance(<A HREF=\"file:$SDK_HOME/sources/android-18/java/lang/reflect/Constructor.java:513\">Constructor.java:513</A>)<BR/>\n" +
        "&nbsp;&nbsp;at android.view.LayoutInflater.rInflate_Original(<A HREF=\"file:$SDK_HOME/sources/android-18/android/view/LayoutInflater.java:755\">LayoutInflater.java:755</A>)<BR/>\n" +
        "&nbsp;&nbsp;at android.view.LayoutInflater_Delegate.rInflate(<A HREF=\"file:$SDK_HOME/sources/android-18/android/view/LayoutInflater_Delegate.java:64\">LayoutInflater_Delegate.java:64</A>)<BR/>\n" +
        "&nbsp;&nbsp;at android.view.LayoutInflater.rInflate(<A HREF=\"file:$SDK_HOME/sources/android-18/android/view/LayoutInflater.java:727\">LayoutInflater.java:727</A>)<BR/>\n" +
        "&nbsp;&nbsp;at android.view.LayoutInflater.inflate(<A HREF=\"file:$SDK_HOME/sources/android-18/android/view/LayoutInflater.java:492\">LayoutInflater.java:492</A>)<BR/>\n" +
        "&nbsp;&nbsp;at android.view.LayoutInflater.inflate(<A HREF=\"file:$SDK_HOME/sources/android-18/android/view/LayoutInflater.java:373\">LayoutInflater.java:373</A>)<BR/>\n" +
        "<BR/>\n" +
        "</body></html>",
        html);
    } else {
      assertEquals(
        "<html><body><A HREF=\"action:close\"></A><font style=\"font-weight:bold; color:#005555;\">Rendering Problems</font><BR/>\n" +
        "java.lang.ArithmeticException: / by zero<BR/>\n" +
        "&nbsp;&nbsp;at com.example.myapplication574.MyCustomView.&lt;init>(<A HREF=\"open:com.example.myapplication574.MyCustomView#<init>;MyCustomView.java:13\">MyCustomView.java:13</A>)<BR/>\n" +
        "&nbsp;&nbsp;at java.lang.reflect.Constructor.newInstance(Constructor.java:513)<BR/>\n" +
        "&nbsp;&nbsp;at android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:755)<BR/>\n" +
        "&nbsp;&nbsp;at android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)<BR/>\n" +
        "&nbsp;&nbsp;at android.view.LayoutInflater.rInflate(LayoutInflater.java:727)<BR/>\n" +
        "&nbsp;&nbsp;at android.view.LayoutInflater.inflate(LayoutInflater.java:492)<BR/>\n" +
        "&nbsp;&nbsp;at android.view.LayoutInflater.inflate(LayoutInflater.java:373)<BR/>\n" +
        "<A HREF=\"runnable:0\">Copy stack to clipboard</A><BR/>\n" +
        "</body></html>",
        html);
    }
  }

  public void testMismatchedBinary() throws Exception {
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml");
    assertNotNull(file);
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    assertNotNull(facet);
    ConfigurationManager configurationManager = facet.getConfigurationManager();
    assertNotNull(configurationManager);
    Configuration configuration = configurationManager.getConfiguration(file);
    RenderLogger logger = new RenderLogger("mylogger", myModule);
    RenderService service = RenderService.create(facet, myModule, psiFile, configuration, logger, null);
    assertNotNull(service);
    RenderResult render = service.render();
    assertNotNull(render);

    Throwable throwable = createExceptionFromDesc(
      "org.xmlpull.v1.XmlPullParserException: unterminated entity ref (position:TEXT \u0050PNG\u001A\u0000\u0000\u0000" +
      "IHDR\u0000...@8:38 in java.io.InputStreamReader@12caea1b)\n" +
      "\tat org.kxml2.io.KXmlParser.exception(Unknown Source)\n" +
      "\tat org.kxml2.io.KXmlParser.error(Unknown Source)\n" +
      "\tat org.kxml2.io.KXmlParser.pushEntity(Unknown Source)\n" +
      "\tat org.kxml2.io.KXmlParser.pushText(Unknown Source)\n" +
      "\tat org.kxml2.io.KXmlParser.nextImpl(Unknown Source)\n" +
      "\tat org.kxml2.io.KXmlParser.next(Unknown Source)\n" +
      "\tat com.android.layoutlib.bridge.android.BridgeXmlBlockParser.next(BridgeXmlBlockParser.java:301)\n" +
      "\tat android.content.res.ColorStateList.createFromXml(ColorStateList.java:122)\n" +
      "\tat android.content.res.BridgeTypedArray.getColorStateList(BridgeTypedArray.java:373)\n" +
      "\tat android.widget.TextView.<init>(TextView.java:956)\n" +
      "\tat android.widget.Button.<init>(Button.java:107)\n" +
      "\tat android.widget.Button.<init>(Button.java:103)\n" +
      "\tat sun.reflect.GeneratedConstructorAccessor53.newInstance(Unknown Source)\n" +
      "\tat sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:27)\n" +
      "\tat java.lang.reflect.Constructor.newInstance(Constructor.java:513)\n" +
      "\tat android.view.LayoutInflater.createView(LayoutInflater.java:594)\n" +
      "\tat android.view.BridgeInflater.onCreateView(BridgeInflater.java:86)\n" +
      "\tat android.view.LayoutInflater.onCreateView(LayoutInflater.java:669)\n" +
      "\tat android.view.LayoutInflater.createViewFromTag(LayoutInflater.java:694)\n" +
      "\tat android.view.BridgeInflater.createViewFromTag(BridgeInflater.java:131)\n" +
      "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:755)\n" +
      "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)\n" +
      "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:727)\n" +
      "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:758)\n" +
      "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)\n" +
      "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:727)\n" +
      "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:758)\n" +
      "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)\n" +
      "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:727)\n" +
      "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:492)\n" +
      "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:373)\n" +
      "\tat com.android.layoutlib.bridge.impl.RenderSessionImpl.inflate(RenderSessionImpl.java:400)\n" +
      "\tat com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:336)\n" +
      "\tat com.android.ide.common.rendering.LayoutLibrary.createSession(LayoutLibrary.java:332)\n" +
      "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:527)\n" +
      "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:520)\n" +
      "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:957)\n" +
      "\tat com.android.tools.idea.rendering.RenderService.createRenderSession(RenderService.java:520)\n" +
      "\tat com.android.tools.idea.rendering.RenderService.render(RenderService.java:557)\n" +
      "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$9.compute(AndroidLayoutPreviewToolWindowManager.java:418)\n" +
      "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$9.compute(AndroidLayoutPreviewToolWindowManager.java:411)\n" +
      "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:968)\n" +
      "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager.doRender(AndroidLayoutPreviewToolWindowManager.java:411)\n" +
      "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager.access$1100(AndroidLayoutPreviewToolWindowManager.java:79)\n" +
      "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$8$1.run(AndroidLayoutPreviewToolWindowManager.java:373)\n" +
      "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl$2.run(ProgressManagerImpl.java:178)\n" +
      "\tat com.intellij.openapi.progress.ProgressManager.executeProcessUnderProgress(ProgressManager.java:209)\n" +
      "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl.executeProcessUnderProgress(ProgressManagerImpl.java:212)\n" +
      "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl.runProcess(ProgressManagerImpl.java:171)\n" +
      "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$8.run(AndroidLayoutPreviewToolWindowManager.java:368)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:320)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:310)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue$2.run(MergingUpdateQueue.java:254)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:269)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:227)\n" +
      "\tat com.intellij.util.ui.update.MergingUpdateQueue.run(MergingUpdateQueue.java:217)\n" +
      "\tat com.intellij.util.concurrency.QueueProcessor.runSafely(QueueProcessor.java:237)\n" +
      "\tat com.intellij.util.Alarm$Request$1.run(Alarm.java:297)\n" +
      "\tat java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:439)\n" +
      "\tat java.util.concurrent.FutureTask$Sync.innerRun(FutureTask.java:303)\n" +
      "\tat java.util.concurrent.FutureTask.run(FutureTask.java:138)\n" +
      "\tat java.util.concurrent.ThreadPoolExecutor$Worker.runTask(ThreadPoolExecutor.java:895)\n" +
      "\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:918)\n" +
      "\tat java.lang.Thread.run(Thread.java:680)\n");
    String path = FileUtil.toSystemDependentName("/foo/bar/baz.png");
    logger.error(null, "Failed to configure parser for " + path, throwable, null);

    assertTrue(logger.hasProblems());
    RenderErrorPanel panel = new RenderErrorPanel();
    String html = panel.showErrors(render);
    assert html != null;
    html = stripImages(html);

    assertEquals(
      "<html><body><A HREF=\"action:close\"></A><font style=\"font-weight:bold; color:#005555;\">Rendering Problems</font><BR/>\n" +
      "Resource error: Attempted to load a bitmap as a color state list.<BR/>\n" +
      "Verify that your style/theme attributes are correct, and make sure layouts are using the right attributes.<BR/>\n" +
      "<BR/>\n" +
      "The relevant image is " + path + "<BR/>\n" +
      "<BR/>\n" +
      "Widgets possibly involved: Button, TextView<BR/>\n" +
      "</body></html>",
      html);
  }

  public void testWrongClassFormat() {
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml");
    assertNotNull(file);
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    assertNotNull(facet);
    ConfigurationManager configurationManager = facet.getConfigurationManager();
    assertNotNull(configurationManager);
    Configuration configuration = configurationManager.getConfiguration(file);
    RenderLogger logger = new RenderLogger("mylogger", myModule);
    RenderService service = RenderService.create(facet, myModule, psiFile, configuration, logger, null);
    assertNotNull(service);
    RenderResult render = service.render();
    assertNotNull(render);

    // MANUALLY register errors
    logger.addIncorrectFormatClass("com.example.unit.test.R",
                                   new InconvertibleClassError(null, "com.example.unit.test.R", 51, 0));
    logger.addIncorrectFormatClass("com.example.unit.test.MyButton",
                                   new InconvertibleClassError(null, "com.example.unit.test.MyButton", 52, 0));

    assertTrue(logger.hasProblems());
    RenderErrorPanel panel = new RenderErrorPanel();
    String html = panel.showErrors(render);
    assert html != null;
    html = stripImages(html);

    String current = ClassConverter.getCurrentJdkVersion();

    assertEquals(
      "<html><body><A HREF=\"action:close\"></A><font style=\"font-weight:bold; color:#005555;\">Rendering Problems</font><BR/>\n" +
      "Preview might be incorrect: unsupported class version.<BR/>\n" +
      "Tip: You need to run the IDE with the highest JDK version that you are compiling custom views with. " +
      "One or more views have been compiled with JDK 1.8, but you are running the IDE on JDK " + current + ". Running on a higher " +
      "JDK is necessary such that these classes can be run in the layout renderer. (Or, extract your custom views into a " +
      "library which you compile with a lower JDK version.)<BR/>\n" +
      "<BR/>\n" +
      "If you have just accidentally built your code with a later JDK, try to <A HREF=\"action:build\">build</A> the project.<BR/>\n" +
      "<BR/>\n" +
      "Classes with incompatible format:<DL>\n" +
      "<DD>-&NBSP;com.example.unit.test.MyButton (Compiled with 1.8)\n" +
      "<DD>-&NBSP;com.example.unit.test.R (Compiled with 1.7)\n" +
      "</DL><A HREF=\"runnable:0\">Rebuild project with '-target 1.6'</A><BR/>\n" +
      "</body></html>",
      html);
  }

  // Image paths will include full resource urls which depends on the test environment
  private static String stripImages(@NotNull String html) {
    while (true) {
      int index = html.indexOf("<img");
      if (index == -1) {
        return html;
      }
      int end = html.indexOf('>', index);
      if (end == -1) {
        return html;
      } else {
        html = html.substring(0, index) + html.substring(end + 1);
      }
    }
  }

  private String stripSdkHome(@NotNull String html) {
    AndroidPlatform platform = AndroidPlatform.getInstance(myModule);
    assertNotNull(platform);
    String location = platform.getSdkData().getLocation();
    location = FileUtil.toSystemIndependentName(location);
    html = html.replace(location, "$SDK_HOME");
    return html;
  }

  /** Attempts to create an exception object that matches the given description, which
   * is in the form of the output of an exception stack dump ({@link Throwable#printStackTrace()})
   *
   * @param desc the description of an exception
   * @return a corresponding exception if possible
   */
  @SuppressWarnings("ThrowableInstanceNeverThrown")
  private static Throwable createExceptionFromDesc(String desc) {
    // First line: description and type
    Iterator<String> iterator = Splitter.on('\n').split(desc).iterator();
    assertTrue(iterator.hasNext());
    String first = iterator.next();
    assertTrue(iterator.hasNext());
    String message = null;
    String exceptionClass;
    int index = first.indexOf(':');
    if (index != -1) {
      exceptionClass = first.substring(0, index).trim();
      message = first.substring(index + 1).trim();
    } else {
      exceptionClass = first.trim();
    }

    Throwable throwable;
    try {
      @SuppressWarnings("unchecked")
      Class<Throwable> clz = (Class<Throwable>)Class.forName(exceptionClass);
      if (message == null) {
        throwable = clz.newInstance();
      } else {
        Constructor<Throwable> constructor = clz.getConstructor(String.class);
        throwable = constructor.newInstance(message);
      }
    } catch (Throwable t) {
      throwable = message != null ? new Throwable(message) : new Throwable();
    }

    List<StackTraceElement> frames = Lists.newArrayList();
    Pattern outerPattern = Pattern.compile("\tat (.*)\\.([^.]*)\\((.*)\\)");
    Pattern innerPattern = Pattern.compile("(.*):(\\d*)");
    while (iterator.hasNext()) {
      String line = iterator.next();
      if (line.isEmpty()) {
        break;
      }
      Matcher outerMatcher = outerPattern.matcher(line);
      if (!outerMatcher.matches()) {
        fail("Line " + line + " does not match expected stactrace pattern");
      } else {
        String clz = outerMatcher.group(1);
        String method = outerMatcher.group(2);
        String inner = outerMatcher.group(3);
        if (inner.equals("Native Method")) {
          frames.add(new StackTraceElement(clz, method, null, -2));
        } else if (inner.equals("Unknown Source")) {
          frames.add(new StackTraceElement(clz, method, null, -1));
        } else {
          Matcher innerMatcher = innerPattern.matcher(inner);
          if (!innerMatcher.matches()) {
            fail("Trace parameter list " + inner + " does not match expected pattern");
          } else {
            String file = innerMatcher.group(1);
            int lineNum = Integer.parseInt(innerMatcher.group(2));
            frames.add(new StackTraceElement(clz, method, file, lineNum));
          }
        }
      }
    }

    throwable.setStackTrace(frames.toArray(new StackTraceElement[frames.size()]));

    // Dump stack back to string to make sure we have the same exception
    assertEquals(desc, AndroidCommonUtils.getStackTrace(throwable));

    return throwable;
  }
}
