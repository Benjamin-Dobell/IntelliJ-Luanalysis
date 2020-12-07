// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.comment.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.tang.intellij.lua.psi.LuaClassMember;
import com.tang.intellij.lua.psi.Visibility;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;

public interface LuaDocGenericTableTy extends LuaDocTy, LuaClassMember {

  @NotNull
  List<LuaDocTy> getTyList();

  @NotNull
  ITy getType();

  @NotNull
  Visibility getVisibility();

  @NotNull
  ITy guessType(@NotNull SearchContext context);

  @NotNull
  ITy guessParentType(@NotNull SearchContext context);

  boolean isDeprecated();

  @Nullable
  LuaDocTy getKeyType();

  @Nullable
  LuaDocTy getValueType();

}
