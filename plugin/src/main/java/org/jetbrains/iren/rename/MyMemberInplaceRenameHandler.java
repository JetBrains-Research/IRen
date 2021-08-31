package org.jetbrains.iren.rename;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.IdNamesSuggestingService;
import org.jetbrains.iren.ModelStatsService;
import org.jetbrains.iren.contributors.ProjectVariableNamesContributor;

import java.awt.*;

public class MyMemberInplaceRenameHandler extends MemberInplaceRenameHandler {
    @Override
    public @Nullable InplaceRefactoring doRename(@NotNull PsiElement elementToRename,
                                                 @NotNull Editor editor,
                                                 @Nullable DataContext dataContext) {
        if (dataContext == null) return null;
        @Nullable Language language = LangDataKeys.LANGUAGE.getData(dataContext);
        if (language != null && language.is(JavaLanguage.INSTANCE) &&
                ModelStatsService.getInstance().isLoaded(ProjectVariableNamesContributor.class, editor.getProject())) {
            Component contextComponent = ObjectUtils.notNull(PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext), editor.getComponent());
            String newName = PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext);
            PsiElement newElementToRename = null;
            if (elementToRename instanceof PsiNameIdentifierOwner) {
                final RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(elementToRename);
                if (processor.isInplaceRenameSupported()) {
                    final StartMarkAction startMarkAction = StartMarkAction.canStart(editor);
                    if (startMarkAction == null || (newElementToRename = processor.substituteElementToRename(elementToRename, editor)) == elementToRename) {
                        processor.substituteElementToRename(elementToRename, editor, new Pass<PsiElement>() {
                            @Override
                            public void pass(PsiElement element) {
                                if (elementToRename instanceof PsiVariable) {
                                    final MyMemberInplaceRenamer renamer = new MyMemberInplaceRenamer((PsiNameIdentifierOwner) elementToRename, element, editor);
                                    if (!renamer.performInplaceRefactoring(IdNamesSuggestingService.getInstance().suggestVariableName((PsiVariable) elementToRename))) {
                                        performDialogRename(elementToRename, editor, createDataContext(contextComponent, newName, elementToRename), renamer.getInitialName());
                                    }
                                } else {
                                    final MemberInplaceRenamer renamer = createMemberRenamer(element, (PsiNameIdentifierOwner) elementToRename, editor);
                                    if (!renamer.performInplaceRename()) {
                                        performDialogRename(elementToRename, editor, createDataContext(contextComponent, newName, elementToRename), renamer.getInitialName());
                                    }
                                }
                            }
                        });
                        return null;
                    } else {
                        final InplaceRefactoring inplaceRefactoring = editor.getUserData(InplaceRefactoring.INPLACE_RENAMER);
                        if (inplaceRefactoring != null && inplaceRefactoring.getClass() == MyMemberInplaceRenamer.class) {
                            final TemplateState templateState = TemplateManagerImpl.getTemplateState(InjectedLanguageEditorUtil.getTopLevelEditor(editor));
                            if (templateState != null) {
                                templateState.gotoEnd(true);
                            }
                        }
                    }
                }
            }
            performDialogRename(elementToRename, editor, createDataContext(contextComponent, newName, newElementToRename), null);
        }
        return super.doRename(elementToRename, editor, dataContext);
    }

    private static DataContext createDataContext(Component contextComponent, String newName, PsiElement newElementToRename) {
        DataContext context = DataManager.getInstance().getDataContext(contextComponent);
        if (newName == null && newElementToRename == null) return context;
        return SimpleDataContext.builder()
                .setParent(context)
                .add(PsiElementRenameHandler.DEFAULT_NAME, newName)
                .add(LangDataKeys.PSI_ELEMENT_ARRAY, newElementToRename == null ? null : new PsiElement[]{newElementToRename})
                .build();
    }

    protected @NotNull MyMemberInplaceRenamer createMyMemberRenamer(@NotNull PsiElement element, @NotNull PsiNameIdentifierOwner elementToRename, @NotNull Editor editor) {
        return new MyMemberInplaceRenamer(elementToRename, element, editor);
    }
}
