// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.tang.intellij.lua.stubs.LuaUnaryExprStub;
import com.intellij.psi.StubBasedPsiElement;

public interface LuaUnaryExpr extends LuaExpression<LuaUnaryExprStub>, StubBasedPsiElement<LuaUnaryExprStub> {

  @Nullable
  LuaCallExpr getCallExpr();

  @Nullable
  LuaIndexExpr getIndexExpr();

  @Nullable
  LuaLiteralExpr getLiteralExpr();

  @Nullable
  LuaNameExpr getNameExpr();

  @Nullable
  LuaParenExpr getParenExpr();

  @Nullable
  LuaTableExpr getTableExpr();

  @Nullable
  LuaUnaryExpr getUnaryExpr();

  @NotNull
  LuaUnaryOp getUnaryOp();

  @Nullable
  LuaExpression<?> getExpression();

}
