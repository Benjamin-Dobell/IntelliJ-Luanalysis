// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.tang.intellij.lua.stubs.LuaBinaryExprStub;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.tree.IElementType;

public interface LuaBinaryExpr extends LuaExpression<LuaBinaryExprStub>, StubBasedPsiElement<LuaBinaryExprStub> {

  @NotNull
  LuaBinaryOp getBinaryOp();

  @NotNull
  IElementType getOperationType();

}
