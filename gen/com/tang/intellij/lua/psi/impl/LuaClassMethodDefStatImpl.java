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
import com.tang.intellij.lua.stubs.LuaClassMethodDefStatStub;
import com.tang.intellij.lua.psi.*;
import com.intellij.navigation.ItemPresentation;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;

public class LuaClassMethodDefStatImpl extends StubBasedPsiElementBase<LuaClassMethodDefStatStub> implements LuaClassMethodDefStat {

  public LuaClassMethodDefStatImpl(@NotNull LuaClassMethodDefStatStub stub, @NotNull IStubElementType<?, ?> nodeType) {
    super(stub, nodeType);
  }

  public LuaClassMethodDefStatImpl(@NotNull ASTNode node) {
    super(node);
  }

  public LuaClassMethodDefStatImpl(LuaClassMethodDefStatStub stub, IElementType type, ASTNode node) {
    super(stub, type, node);
  }

  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitClassMethodDefStat(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public LuaClassMethodName getClassMethodName() {
    return notNullChild(PsiTreeUtil.getStubChildOfType(this, LuaClassMethodName.class));
  }

  @Override
  @Nullable
  public LuaFuncBody getFuncBody() {
    return PsiTreeUtil.getStubChildOfType(this, LuaFuncBody.class);
  }

  @Override
  @NotNull
  public ITy guessParentType(@NotNull SearchContext context) {
    return LuaPsiImplUtilKt.guessParentType(this, context);
  }

  @Override
  @NotNull
  public Visibility getVisibility() {
    return LuaPsiImplUtilKt.getVisibility(this);
  }

  @Override
  public boolean isDeprecated() {
    return LuaPsiImplUtilKt.isDeprecated(this);
  }

  @Override
  public boolean isExplicitlyTyped() {
    return LuaPsiImplUtilKt.isExplicitlyTyped(this);
  }

  @Override
  @NotNull
  public List<LuaParamDef> getParamDefList() {
    return LuaPsiImplUtilKt.getParamDefList(this);
  }

  @Override
  @Nullable
  public PsiElement getNameIdentifier() {
    return LuaPsiImplUtilKt.getNameIdentifier(this);
  }

  @Override
  @NotNull
  public PsiElement setName(@NotNull String name) {
    return LuaPsiImplUtilKt.setName(this, name);
  }

  @Override
  @Nullable
  public String getName() {
    return LuaPsiImplUtilKt.getName(this);
  }

  @Override
  public int getTextOffset() {
    return LuaPsiImplUtilKt.getTextOffset(this);
  }

  @Override
  @NotNull
  public String toString() {
    return LuaPsiImplUtilKt.toString(this);
  }

  @Override
  @Nullable
  public ITy guessReturnType(@NotNull SearchContext context) {
    return LuaPsiImplUtilKt.guessReturnType(this, context);
  }

  @Override
  @NotNull
  public LuaParamInfo[] getParams() {
    return LuaPsiImplUtilKt.getParams(this);
  }

  @Override
  public boolean isStatic() {
    return LuaPsiImplUtilKt.isStatic(this);
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return LuaPsiImplUtilKt.getPresentation(this);
  }

}
