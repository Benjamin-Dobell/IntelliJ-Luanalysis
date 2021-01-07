// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import com.tang.intellij.lua.stubs.LuaTableFieldStub;
import com.intellij.navigation.ItemPresentation;
import com.tang.intellij.lua.comment.psi.LuaDocTy;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;

public interface LuaTableField extends LuaClassField, PsiNameIdentifierOwner, LuaCommentOwner, LuaTypeScope, StubBasedPsiElement<LuaTableFieldStub> {

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
  ITy guessIndexType(@NotNull SearchContext context);

  @NotNull
  ITy guessParentType(@NotNull SearchContext context);

  @Nullable
  LuaDocTy getIndexType();

  @NotNull
  Visibility getVisibility();

  boolean isDeprecated();

  boolean isExplicitlyTyped();

  @Nullable
  LuaExpression<?> getIdExpr();

  @Nullable
  LuaExpression<?> getValueExpr();

  @NotNull
  List<LuaExpression<?>> getExpressionList();

  @Nullable
  PsiElement getLbrack();

}
