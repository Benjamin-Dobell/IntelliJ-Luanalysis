// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.tang.intellij.lua.stubs.LuaExprPlaceStub;
import com.intellij.psi.StubBasedPsiElement;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;

public interface LuaCallExpr extends LuaExpression<LuaExprPlaceStub>, StubBasedPsiElement<LuaExprPlaceStub> {

  @NotNull
  LuaArgs getArgs();

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
  ITy guessParentType(@NotNull SearchContext context);

  @Nullable
  PsiElement getFirstStringArg();

  boolean isMethodDotCall();

  boolean isMethodColonCall();

  boolean isFunctionCall();

  @NotNull
  LuaExpression<?> getExpression();

}
