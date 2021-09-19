// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.comment.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.tang.intellij.lua.psi.LuaPsiTypeMember;
import com.tang.intellij.lua.psi.Visibility;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;

public interface LuaDocGenericTableTy extends LuaDocTy, LuaPsiTypeMember
{

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

  boolean isExplicitlyTyped();

  @Nullable
  LuaDocTy getKeyType();

  @Nullable
  LuaDocTy getValueType();

}
