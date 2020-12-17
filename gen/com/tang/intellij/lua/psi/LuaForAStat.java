// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.tang.intellij.lua.stubs.LuaPlaceholderStub;
import com.intellij.psi.StubBasedPsiElement;

public interface LuaForAStat extends LuaStatement, LuaParametersOwner<LuaPlaceholderStub>, LuaLoop, LuaIndentRange, LuaDeclarationScope, StubBasedPsiElement<LuaPlaceholderStub> {

  @NotNull
  LuaParamDef getParamDef();

  @NotNull
  List<LuaParamDef> getParamDefList();

}
