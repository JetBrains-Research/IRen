package org.jetbrains.iren.rename;

import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinMemberInplaceRenameHandler;
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinRenameDispatcherHandler;
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinVariableInplaceRenameHandler;

public class KotlinHandlersRemover implements DefaultHandlersRemover {
    @Override
    public void removeHandlers() {
        RenameHandler.EP_NAME.getPoint().unregisterExtension(KotlinRenameDispatcherHandler.class);
        RenameHandler.EP_NAME.getPoint().unregisterExtension(KotlinMemberInplaceRenameHandler.class);
        RenameHandler.EP_NAME.getPoint().unregisterExtension(KotlinVariableInplaceRenameHandler.class);
    }
}
