// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.comment.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;

public interface LuaDocTypeRef extends LuaDocPsiElement {

  @Nullable
  PsiElement getId();

  @NotNull
  String getName();

  @NotNull
  PsiReference getReference();

  @NotNull
  ITy resolveType(@NotNull SearchContext context);

}
