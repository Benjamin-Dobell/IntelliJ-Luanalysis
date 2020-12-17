// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.tang.intellij.lua.stubs.LuaIndexExprStub;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.navigation.ItemPresentation;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;

public interface LuaIndexExpr extends LuaClassMember, LuaExpression<LuaIndexExprStub>, PsiNameIdentifierOwner, StubBasedPsiElement<LuaIndexExprStub> {

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
  PsiElement getId();

  @Nullable
  PsiElement getNameIdentifier();

  @NotNull
  PsiElement setName(@NotNull String name);

  @Nullable
  String getName();

  int getTextOffset();

  @NotNull
  ItemPresentation getPresentation();

  @Nullable
  LuaExpression<?> getIdExpr();

  @Nullable
  ITy guessIndexType(@NotNull SearchContext context);

  @NotNull
  ITy guessParentType(@NotNull SearchContext context);

  boolean isDeprecated();

  @NotNull
  List<LuaExpression<?>> getExpressionList();

  @Nullable
  PsiElement getDot();

  @Nullable
  PsiElement getColon();

  @Nullable
  PsiElement getLbrack();

}
