// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.comment.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.tang.intellij.lua.psi.LuaTypeField;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import com.tang.intellij.lua.stubs.LuaDocTableFieldStub;
import com.tang.intellij.lua.psi.Visibility;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;

public interface LuaDocTableField extends LuaDocPsiElement, LuaTypeField, PsiNameIdentifierOwner, StubBasedPsiElement<LuaDocTableFieldStub> {

  @NotNull
  List<LuaDocTy> getTyList();

  @Nullable
  PsiElement getId();

  @NotNull
  ITy guessParentType(@NotNull SearchContext context);

  @NotNull
  Visibility getVisibility();

  @NotNull
  PsiElement setName(@NotNull String newName);

  @Nullable
  String getName();

  @Nullable
  PsiElement getNameIdentifier();

  @Nullable
  LuaDocTy getIndexType();

  @Nullable
  LuaDocTy getValueType();

  @Nullable
  ITy guessIndexType(@NotNull SearchContext context);

  @NotNull
  ITy guessType(@NotNull SearchContext context);

  boolean isDeprecated();

  boolean isExplicitlyTyped();

  @Nullable
  PsiElement getLbrack();

}
