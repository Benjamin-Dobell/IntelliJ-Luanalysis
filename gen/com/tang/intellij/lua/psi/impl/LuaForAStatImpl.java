// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.tang.intellij.lua.psi.LuaTypes.*;
import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.tang.intellij.lua.stubs.LuaPlaceholderStub;
import com.tang.intellij.lua.psi.*;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;

public class LuaForAStatImpl extends StubBasedPsiElementBase<LuaPlaceholderStub> implements LuaForAStat {

  public LuaForAStatImpl(@NotNull LuaPlaceholderStub stub, @NotNull IStubElementType type) {
    super(stub, type);
  }

  public LuaForAStatImpl(@NotNull ASTNode node) {
    super(node);
  }

  public LuaForAStatImpl(LuaPlaceholderStub stub, IElementType type, ASTNode node) {
    super(stub, type, node);
  }

  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitForAStat(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public LuaParamDef getParamDef() {
    return notNullChild(PsiTreeUtil.getStubChildOfType(this, LuaParamDef.class));
  }

  @Override
  @NotNull
  public List<LuaParamDef> getParamDefList() {
    return LuaPsiImplUtilKt.getParamDefList(this);
  }

}
