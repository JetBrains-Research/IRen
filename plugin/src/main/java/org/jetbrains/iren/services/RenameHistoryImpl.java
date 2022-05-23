package org.jetbrains.iren.services;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.LanguageSupporter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@State(name = "IRenRenameHistory", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class RenameHistoryImpl implements PersistentStateComponent<RenameHistoryImpl>, RenameHistory {
    public BlockingQueue<String> rememberedNames = new LinkedBlockingQueue<>(100);
    private final String delimiter = "-";

    @Override
    public synchronized void rememberVariableName(@NotNull String variableHash, @NotNull String insertedName) {
        String hash = String.join(delimiter, variableHash, insertedName);
        rememberedNames.remove(hash);
        if (rememberedNames.remainingCapacity() == 0) {
            rememberedNames.remove();
        }
        rememberedNames.add(hash);
    }

    @Override
    public boolean isRenamedVariable(@NotNull PsiNameIdentifierOwner variable) {
        return rememberedNames.contains(getVariableHash(variable, true));
    }

    @Override
    public String getVariableHash(PsiNamedElement variable, boolean insertName) {
        ArrayList<String> nameList = new ArrayList<>(5);
        if (insertName) nameList.add(variable.getName());
        LanguageSupporter supporter = LanguageSupporter.getInstance(variable.getLanguage());
        PsiElement parent = null;
        if (supporter != null) {
            parent = variable.getParent();
            while (parent != null && !(parent instanceof PsiFile)) {
                if (parent instanceof PsiNameIdentifierOwner && supporter.isFunctionOrClass((PsiNameIdentifierOwner) parent)) {
                    nameList.add(((PsiNameIdentifierOwner) parent).getName());
                }
                parent = parent.getParent();
            }
        }
        final VirtualFile file = parent != null ? ((PsiFile) parent).getVirtualFile() : variable.getContainingFile().getVirtualFile();
        nameList.add(file.getPath());
        Collections.reverse(nameList);
        return String.join(delimiter, nameList);
    }

    @Override
    public @Nullable RenameHistoryImpl getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull RenameHistoryImpl state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
