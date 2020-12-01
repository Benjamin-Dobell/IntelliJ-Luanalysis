// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.comment.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.tang.intellij.lua.psi.LuaTypeScope;
import com.tang.intellij.lua.psi.LuaParamInfo;
import com.tang.intellij.lua.ty.ITy;

public interface LuaDocFunctionTy extends LuaDocTy, LuaTypeScope {

  @Nullable
  LuaDocFunctionParams getFunctionParams();

  @Nullable
  LuaDocFunctionReturnType getFunctionReturnType();

  @NotNull
  List<LuaDocGenericDef> getGenericDefList();

  @NotNull
  ITy getType();

  @Nullable
  LuaParamInfo[] getParams();

  @Nullable
  ITy getVarargParam();

  @Nullable
  ITy getReturnType();

}
