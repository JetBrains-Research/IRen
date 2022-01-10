package org.jetbrains.iren.services;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.api.LanguageSupporter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@State(name = "IRenRenameHistory", storages = @Storage("IRenRenameHistory.xml"))
public class RenameHistory implements PersistentStateComponent<RenameHistory> {
    public BlockingQueue<String> rememberedNames = new LinkedBlockingQueue<>(100);
    private final String delimiter = "-";

    public static @NotNull RenameHistory getInstance(Project project) {
        return project.getService(RenameHistory.class);
    }

    public synchronized void rememberVariableName(@NotNull String variableHash, @NotNull String insertedName) {
        String hash = String.join(delimiter, variableHash, insertedName);
        rememberedNames.remove(hash);
        if (rememberedNames.remainingCapacity() == 0) {
            rememberedNames.remove();
        }
        rememberedNames.add(hash);
    }

    public boolean isRenamedVariable(@NotNull PsiNameIdentifierOwner variable) {
        return rememberedNames.contains(getVariableHash(variable, true));
    }

    public String getVariableHash(PsiNamedElement variable, boolean insertName) {
//        TODO: mb add caching
        ArrayList<String> nameList = new ArrayList<>(5);
        if (insertName) nameList.add(variable.getName());
        LanguageSupporter supporter = LanguageSupporter.getInstance(variable.getLanguage());
        PsiElement parent = variable.getParent();
        while (parent != null && !(parent instanceof PsiFile)) {
            if (parent instanceof PsiNameIdentifierOwner && supporter.shouldAddToHash((PsiNameIdentifierOwner) parent)) {
                nameList.add(((PsiNameIdentifierOwner) parent).getName());
            }
            parent = parent.getParent();
        }
        final VirtualFile file = parent != null ? ((PsiFile) parent).getVirtualFile() : variable.getContainingFile().getVirtualFile();
        nameList.add(file.getPath());
        Collections.reverse(nameList);
        return String.join(delimiter, nameList);
    }

    @Override
    public @Nullable RenameHistory getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull RenameHistory state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
