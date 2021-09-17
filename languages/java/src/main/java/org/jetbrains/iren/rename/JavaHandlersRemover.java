package org.jetbrains.iren.rename;

import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;

public class JavaHandlersRemover implements DefaultHandlersRemover {
    @Override
    public void removeHandlers() {
        RenameHandler.EP_NAME.getPoint().unregisterExtension(VariableInplaceRenameHandler.class);
        RenameHandler.EP_NAME.getPoint().unregisterExtension(MemberInplaceRenameHandler.class);
    }
}
