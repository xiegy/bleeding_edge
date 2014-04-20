/*
 * Copyright (c) 2014, the Dart project authors.
 * 
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.dart.tools.ui.internal.text.editor;

import com.google.common.base.Objects;
import com.google.dart.server.Outline;
import com.google.dart.server.OutlineKind;
import com.google.dart.tools.core.DartCore;
import com.google.dart.tools.internal.search.ui.DartSearchActionGroup;
import com.google.dart.tools.ui.DartElementImageDescriptor;
import com.google.dart.tools.ui.DartPluginImages;
import com.google.dart.tools.ui.DartToolsPlugin;
import com.google.dart.tools.ui.actions.InstrumentedAction;
import com.google.dart.tools.ui.actions.OpenViewActionGroup;
import com.google.dart.tools.ui.actions.RefactorActionGroup;
import com.google.dart.tools.ui.instrumentation.UIInstrumentationBuilder;
import com.google.dart.tools.ui.internal.text.DartHelpContextIds;
import com.google.dart.tools.ui.internal.util.SWTUtil;
import com.google.dart.tools.ui.internal.viewsupport.ColoredViewersManager;
import com.google.dart.tools.ui.internal.viewsupport.ImageDescriptorRegistry;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.part.Page;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

/**
 * {@link IContentOutlinePage} for {@link DartEditor}.
 */
public class DartOutlinePage_NEW extends Page implements IContentOutlinePage {

  /**
   * {@link ViewerComparator} for {@link Outline} names.
   */
  public static class NameComparator extends ViewerComparator {
    private static final int NOT_ELEMENT = 2;
    private static final int PRIVATE_ELEMENT = 1;
    private static final int PUBLIC_ELEMENT = 0;

    @Override
    public int category(Object e) {
      if (!(e instanceof Outline)) {
        return NOT_ELEMENT;
      }
      Outline outline = (Outline) e;
      // TODO(scheglov)
//      if (outline.isPrivate()) {
//        return PRIVATE_ELEMENT;
//      }
      return PUBLIC_ELEMENT;
    }

    @Override
    public int compare(Viewer viewer, Object e1, Object e2) {
      // compare categories
      int cat1 = category(e1);
      int cat2 = category(e2);
      if (cat1 != cat2) {
        return cat1 - cat2;
      }
      // check types
      if (!(e1 instanceof Outline)) {
        return 0;
      }
      if (!(e2 instanceof Outline)) {
        return 0;
      }
      // compare names
      String name1 = ((Outline) e1).getName();
      String name2 = ((Outline) e2).getName();
      if (name1 == null || name2 == null) {
        return 0;
      }
      return name1.compareTo(name2);
    }
  }

  /**
   * {@link ViewerComparator} for {@link Outline} positions.
   */
  public static class PositionComparator extends ViewerComparator {
    @Override
    public int compare(Viewer viewer, Object e1, Object e2) {
      if (!(e1 instanceof Outline)) {
        return 0;
      }
      if (!(e2 instanceof Outline)) {
        return 0;
      }
      int offset1 = ((Outline) e1).getOffset();
      int offset2 = ((Outline) e2).getOffset();
      return offset1 - offset2;
    }
  }

  private class CollapseAllAction extends InstrumentedAction {
    CollapseAllAction() {
      super("Collapse All"); //$NON-NLS-1$
      setDescription("Collapse All"); //$NON-NLS-1$
      setToolTipText("Collapse All"); //$NON-NLS-1$
      DartPluginImages.setLocalImageDescriptors(this, "collapseall.gif"); //$NON-NLS-1$
      PlatformUI.getWorkbench().getHelpSystem().setHelp(
          this,
          DartHelpContextIds.COLLAPSE_ALL_ACTION);
    }

    @Override
    protected void doRun(Event event, UIInstrumentationBuilder instrumentation) {
      viewer.collapseAll();
    }
  }

  private class DartOutlineViewer extends TreeViewer {
    public DartOutlineViewer(final Tree tree) {
      super(tree);
      setUseHashlookup(true);
      tree.setBackgroundMode(SWT.INHERIT_FORCE);
    }

    private void updateColors() {
      SWTUtil.setColors(getTree(), preferences);
    }
  }

  private class ExpandAllAction extends InstrumentedAction {
    ExpandAllAction() {
      super("Expand All"); //$NON-NLS-1$
      setDescription("Expand All"); //$NON-NLS-1$
      setToolTipText("Expand All"); //$NON-NLS-1$
      DartPluginImages.setLocalImageDescriptors(this, "expandall.gif"); //$NON-NLS-1$
      PlatformUI.getWorkbench().getHelpSystem().setHelp(this, DartHelpContextIds.EXPAND_ALL_ACTION);
    }

    @Override
    protected void doRun(Event event, UIInstrumentationBuilder instrumentation) {
      viewer.expandAll();
    }
  }

  private class HideNonPublicAction extends InstrumentedAction {
    HideNonPublicAction() {
      super("Hide non-public members", IAction.AS_CHECK_BOX); //$NON-NLS-1$
      setDescription("Hide non-public members"); //$NON-NLS-1$
      setToolTipText("Hide non-public members"); //$NON-NLS-1$
      DartPluginImages.setLocalImageDescriptors(this, "public_co.gif"); //$NON-NLS-1$
      PlatformUI.getWorkbench().getHelpSystem().setHelp(
          this,
          DartHelpContextIds.HIDE_NON_PUBLIC_ACTION);
    }

    @Override
    protected void doRun(Event event, UIInstrumentationBuilder instrumentation) {
      if (isChecked()) {
        viewer.addFilter(PUBLIC_FILTER);
      } else {
        viewer.removeFilter(PUBLIC_FILTER);
      }
    }
  }

  private class LexicalSortingAction extends InstrumentedAction {
    public LexicalSortingAction() {
      PlatformUI.getWorkbench().getHelpSystem().setHelp(
          this,
          DartHelpContextIds.LEXICAL_SORTING_OUTLINE_ACTION);
      setText(DartEditorMessages.JavaOutlinePage_Sort_label);
      DartPluginImages.setLocalImageDescriptors(this, "alphab_sort_co.gif"); //$NON-NLS-1$
      setToolTipText(DartEditorMessages.JavaOutlinePage_Sort_tooltip);
      setDescription(DartEditorMessages.JavaOutlinePage_Sort_description);
      // restore selection
      boolean checked = DartToolsPlugin.getDefault().getPreferenceStore().getBoolean(
          "LexicalSortingAction.isChecked"); //$NON-NLS-1$
      valueChanged(checked, false);
    }

    @Override
    protected void doRun(Event event, UIInstrumentationBuilder instrumentation) {
      valueChanged(isChecked(), true);
    }

    private void valueChanged(final boolean on, boolean store) {
      setChecked(on);
      // set comparator
      BusyIndicator.showWhile(viewer.getControl().getDisplay(), new Runnable() {
        @Override
        public void run() {
          if (on) {
            viewer.setComparator(NAME_COMPARATOR);
          } else {
            viewer.setComparator(POSITION_COMPARATOR);
          }
        }
      });
      // may be remember selection
      if (store) {
        DartToolsPlugin.getDefault().getPreferenceStore().setValue(
            "LexicalSortingAction.isChecked", on); //$NON-NLS-1$
      }
    }
  }

  private static class OutlineContentProvider implements ITreeContentProvider {
    @Override
    public void dispose() {
    }

    @Override
    public Object[] getChildren(Object parentElement) {
      return ((Outline) parentElement).getChildren();
    }

    @Override
    public Object[] getElements(Object inputElement) {
      return ((Outline) inputElement).getChildren();
    }

    @Override
    public Object getParent(Object element) {
      return ((Outline) element).getParent();
    }

    @Override
    public boolean hasChildren(Object element) {
      return getChildren(element).length != 0;
    }

    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    }
  }
  private static class OutlineLabelProvider extends LabelProvider implements IStyledLabelProvider {
    private static final Point SIZE = new Point(22, 16);
    private static final ImageDescriptorRegistry registry = DartToolsPlugin.getImageDescriptorRegistry();
    private static final String RIGHT_ARROW = " \u2192 "; //$NON-NLS-1$

    private static ImageDescriptor getBaseImageDescriptor(OutlineKind kind, boolean isPrivate) {
      if (kind == OutlineKind.CLASS) {
        return isPrivate ? DartPluginImages.DESC_DART_CLASS_PRIVATE
            : DartPluginImages.DESC_DART_CLASS_PUBLIC;
      }
      if (kind == OutlineKind.FUNCTION_TYPE_ALIAS) {
        return isPrivate ? DartPluginImages.DESC_DART_FUNCTIONTYPE_PRIVATE
            : DartPluginImages.DESC_DART_FUNCTIONTYPE_PUBLIC;
      }
      if (kind == OutlineKind.FIELD || kind == OutlineKind.TOP_LEVEL_VARIABLE) {
        return isPrivate ? DartPluginImages.DESC_DART_FIELD_PRIVATE
            : DartPluginImages.DESC_DART_FIELD_PUBLIC;
      }
      if (kind == OutlineKind.CONSTRUCTOR || kind == OutlineKind.FUNCTION
          || kind == OutlineKind.GETTER || kind == OutlineKind.METHOD || kind == OutlineKind.SETTER) {
        return isPrivate ? DartPluginImages.DESC_DART_METHOD_PRIVATE
            : DartPluginImages.DESC_DART_METHOD_PUBLIC;
      }
      return null;
    }

    private static ImageDescriptor getImageDescriptor(OutlineKind kind, boolean isPrivate) {
      ImageDescriptor base = getBaseImageDescriptor(kind, isPrivate);
      if (base == null) {
        return null;
      }
      int flags = 0;
      if (kind == OutlineKind.CONSTRUCTOR) {
        flags |= DartElementImageDescriptor.CONSTRUCTOR;
      }
      if (kind == OutlineKind.GETTER) {
        flags |= DartElementImageDescriptor.GETTER;
      }
      if (kind == OutlineKind.SETTER) {
        flags |= DartElementImageDescriptor.SETTER;
      }
      // TODO(scheglov) Analysis Server: add flags: abstract, private, static
//      // ClassDeclaration
//      if (node instanceof ClassDeclaration) {
//        ClassDeclaration classDeclaration = (ClassDeclaration) node;
//        if (classDeclaration.getAbstractKeyword() != null) {
//          flags |= DartElementImageDescriptor.ABSTRACT;
//        }
//      }
//      // ConstructorDeclaration
//      if (node instanceof ConstructorDeclaration) {
//        flags |= DartElementImageDescriptor.CONSTRUCTOR;
//      }
//      // MethodDeclaration
//      if (node instanceof MethodDeclaration) {
//        MethodDeclaration method = (MethodDeclaration) node;
//        if (method.isAbstract()) {
//          flags |= DartElementImageDescriptor.ABSTRACT;
//        }
//        if (method.isStatic()) {
//          flags |= DartElementImageDescriptor.STATIC;
//        }
//        if (method.isGetter()) {
//          flags |= DartElementImageDescriptor.GETTER;
//        }
//        if (method.isSetter()) {
//          flags |= DartElementImageDescriptor.SETTER;
//        }
//      }
      // done
      return new DartElementImageDescriptor(base, flags, SIZE);
    }

    @Override
    public Image getImage(Object o) {
      Outline outline = (Outline) o;
      // TODO(scheglov) Analysis Server: add isPrivate() accessor
      boolean isPrivate = outline.getName().startsWith("_");
      ImageDescriptor descriptor = getImageDescriptor(outline.getKind(), isPrivate);
      if (descriptor != null) {
        return registry.get(descriptor);
      }
      return null;
    }

    @Override
    public StyledString getStyledText(Object obj) {
      Outline outline = (Outline) obj;
      StyledString styledString = new StyledString(getText(obj));
      // append parameters
      String parameters = outline.getParameters();
      if (parameters != null) {
        styledString.append(parameters, StyledString.DECORATIONS_STYLER);
      }
      // append return type
      String returnType = outline.getReturnType();
      if (!StringUtils.isEmpty(returnType)) {
        if (outline.getKind() == OutlineKind.FIELD
            || outline.getKind() == OutlineKind.TOP_LEVEL_VARIABLE) {
          styledString.append(" : " + returnType, StyledString.QUALIFIER_STYLER);
        } else {
          styledString.append(RIGHT_ARROW + returnType, StyledString.QUALIFIER_STYLER);
        }
      }
      // done
      return styledString;
    }

    @Override
    public String getText(Object element) {
      return ((Outline) element).getName();
    }
  }

  private final ListenerList selectionChangedListeners = new ListenerList(ListenerList.IDENTITY);
  private final String contextMenuID;
  private DartEditor editor;
  private Outline input;
  private DartOutlineViewer viewer;

  private boolean ignoreSelectionChangedEvent = false;
  private Menu contextMenu;

  private CompositeActionGroup actionGroups;
  private IPreferenceStore preferences;

  private IPropertyChangeListener propertyChangeListener = new IPropertyChangeListener() {
    @Override
    public void propertyChange(PropertyChangeEvent event) {
      doPropertyChange(event);
    }
  };

  private static final ViewerComparator NAME_COMPARATOR = new NameComparator();
  private static final ViewerComparator POSITION_COMPARATOR = new PositionComparator();
  private static final ViewerFilter PUBLIC_FILTER = new ViewerFilter() {
    @Override
    public boolean select(Viewer viewer, Object parentElement, Object o) {
      // TODO(scheglov)
//      if (o instanceof LightNodeElement) {
//        LightNodeElement element = (LightNodeElement) o;
//        return !element.isPrivate();
//      }
      return false;
    }
  };

  public DartOutlinePage_NEW(String contextMenuID, DartEditor editor) {
    Assert.isNotNull(editor);
    this.contextMenuID = contextMenuID;
    this.editor = editor;
  }

  @Override
  public void addSelectionChangedListener(ISelectionChangedListener listener) {
    if (viewer != null) {
      viewer.addSelectionChangedListener(listener);
    } else {
      selectionChangedListeners.add(listener);
    }
  }

  @Override
  public void createControl(Composite parent) {
    preferences = DartToolsPlugin.getDefault().getCombinedPreferenceStore();
    Tree tree = new Tree(parent, SWT.MULTI | SWT.FULL_SELECTION);
    // create "viewer"
    viewer = new DartOutlineViewer(tree);
    ColoredViewersManager.install(viewer);
    viewer.setContentProvider(new OutlineContentProvider());
    viewer.setLabelProvider(new DelegatingStyledCellLabelProvider(new OutlineLabelProvider()));
    SWTUtil.bindJFaceResourcesFontToControl(tree);
    // install listeners added before UI creation
    {
      Object[] listeners = selectionChangedListeners.getListeners();
      for (Object listener : listeners) {
        selectionChangedListeners.remove(listener);
        viewer.addSelectionChangedListener((ISelectionChangedListener) listener);
      }
    }
    // prepare context menu
    MenuManager manager = new MenuManager(contextMenuID, contextMenuID);
    manager.setRemoveAllWhenShown(true);
    manager.addMenuListener(new IMenuListener() {
      @Override
      public void menuAboutToShow(IMenuManager m) {
        contextMenuAboutToShow(m);
      }
    });
    contextMenu = manager.createContextMenu(tree);
    tree.setMenu(contextMenu);
    // register "viewer" as selection provide for this view
    IPageSite site = getSite();
    site.setSelectionProvider(viewer);
    actionGroups = new CompositeActionGroup(new ActionGroup[] {
        new OpenViewActionGroup(site), new RefactorActionGroup(site),
        new DartSearchActionGroup(site)});
    // configure actions
    {
      IActionBars actionBars = site.getActionBars();
      {
        IToolBarManager toolBarManager = actionBars.getToolBarManager();
        toolBarManager.add(new HideNonPublicAction());
        toolBarManager.add(new LexicalSortingAction());
        toolBarManager.add(new ExpandAllAction());
        toolBarManager.add(new CollapseAllAction());
      }
      actionGroups.fillActionBars(actionBars);
    }
    // handle TreeViewer actions
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      @Override
      public void selectionChanged(SelectionChangedEvent event) {
        if (ignoreSelectionChangedEvent) {
          return;
        }
        editor.doSelectionChanged(event.getSelection());
      }
    });
    viewer.addDoubleClickListener(new IDoubleClickListener() {
      @Override
      public void doubleClick(DoubleClickEvent event) {
        toggleExpansion(event.getSelection());
      }
    });
    // update colors
    preferences.addPropertyChangeListener(propertyChangeListener);
    viewer.updateColors();
  }

  @Override
  public void dispose() {
    // clear "editor"
    if (editor == null) {
      return;
    }
    editor.outlinePageClosed();
    editor = null;
    // remove property listeners
    if (propertyChangeListener != null) {
      preferences.removePropertyChangeListener(propertyChangeListener);
      propertyChangeListener = null;
    }
    // dispose "contextMenu"
    if (contextMenu != null && !contextMenu.isDisposed()) {
      contextMenu.dispose();
      contextMenu = null;
    }
    // dispose actions
    if (actionGroups != null) {
      actionGroups.dispose();
      actionGroups = null;
    }
    // we are not selection provider anymore
    getSite().setSelectionProvider(null);
    // done
    viewer = null;
    input = null;
    super.dispose();
  }

  @Override
  public Control getControl() {
    if (viewer != null) {
      return viewer.getControl();
    }
    return null;
  }

  @Override
  public ISelection getSelection() {
    if (viewer == null) {
      return StructuredSelection.EMPTY;
    }
    return viewer.getSelection();
  }

  @Override
  public void removeSelectionChangedListener(ISelectionChangedListener listener) {
    if (viewer != null) {
      viewer.removeSelectionChangedListener(listener);
    } else {
      selectionChangedListeners.remove(listener);
    }
  }

  public void select(final int offset) {
    updateViewerWithoutDraw(new Runnable() {
      @Override
      public void run() {
        // TODO(scheglov) Analysis Server: add source range
//        setSelection(new StructuredSelection(element));
//        viewer.reveal(element);
      }
    });
  }

  @Override
  public void setFocus() {
    if (viewer != null) {
      viewer.getControl().setFocus();
    }
  }

  public void setInput(final Outline input) {
    this.input = input;
    updateViewerWithoutDraw(new Runnable() {
      @Override
      public void run() {
        Object[] expandedElements = viewer.getExpandedElements();
        viewer.setInput(input);
        viewer.setExpandedElements(expandedElements);
      }
    });
  }

  @Override
  public void setSelection(final ISelection newSelection) {
    if (!Objects.equal(viewer.getSelection(), newSelection)) {
      updateViewerWithoutDraw(new Runnable() {
        @Override
        public void run() {
          ignoreSelectionChangedEvent = true;
          try {
            viewer.setSelection(newSelection, true);
          } finally {
            ignoreSelectionChangedEvent = false;
          }
        }
      });
    }
  }

  protected void toggleExpansion(ISelection selection) {
    if (selection instanceof IStructuredSelection) {
      Object sel = ((IStructuredSelection) selection).getFirstElement();
      boolean expanded = viewer.getExpandedState(sel);
      if (expanded) {
        viewer.collapseToLevel(sel, 1);
      } else {
        viewer.expandToLevel(sel, 1);
      }
    }
  }

  private void contextMenuAboutToShow(IMenuManager menu) {
    DartToolsPlugin.createStandardGroups(menu);
    IStructuredSelection selection = (IStructuredSelection) getSelection();
    actionGroups.setContext(new ActionContext(selection));
    actionGroups.fillContextMenu(menu);
  }

  private void doPropertyChange(PropertyChangeEvent event) {
    if (viewer != null) {
      viewer.updateColors();
      viewer.refresh(false);
    }
  }

  /**
   * Performs the given operation over {@link #viewer} while redraw is disabled.
   */
  private void updateViewerWithoutDraw(Runnable runnable) {
    if (viewer != null) {
      final Control control = viewer.getControl().getParent();
      control.setRedraw(false);
      try {
        runnable.run();
      } finally {
        if (DartCore.isLinux()) {
          // By some reason on Linux we need to add a delay.
          // It seems like Tree operations are performed not in the UI thread.
          Display.getCurrent().timerExec(50, new Runnable() {
            @Override
            public void run() {
              control.setRedraw(true);
            }
          });
        } else {
          control.setRedraw(true);
        }
      }
    }
  }
}