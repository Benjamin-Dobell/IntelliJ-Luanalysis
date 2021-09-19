// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.tang.intellij.lua.psi.LuaTypes.*;
import com.tang.intellij.lua.psi.*;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.tang.intellij.lua.stubs.LuaPlaceholderStub;

public class LuaVarListImpl extends LuaExprListImpl implements LuaVarList {

  public LuaVarListImpl(@NotNull LuaPlaceholderStub stub, @NotNull IStubElementType<?, ?> nodeType) {
    super(stub, nodeType);
  }

  public LuaVarListImpl(@NotNull ASTNode node) {
    super(node);
  }

  public LuaVarListImpl(LuaPlaceholderStub stub, IElementType type, ASTNode node) {
    super(stub, type, node);
  }

  @Override
  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitVarList(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<LuaCallExpr> getCallExprList() {
    return PsiTreeUtil.getStubChildrenOfTypeAsList(this, LuaCallExpr.class);
  }

  @Override
  @NotNull
  public List<LuaIndexExpr> getIndexExprList() {
    return PsiTreeUtil.getStubChildrenOfTypeAsList(this, LuaIndexExpr.class);
  }

  @Override
  @NotNull
  public List<LuaLiteralExpr> getLiteralExprList() {
    return PsiTreeUtil.getStubChildrenOfTypeAsList(this, LuaLiteralExpr.class);
  }

  @Override
  @NotNull
  public List<LuaNameExpr> getNameExprList() {
    return PsiTreeUtil.getStubChildrenOfTypeAsList(this, LuaNameExpr.class);
  }

  @Override
  @NotNull
  public List<LuaParenExpr> getParenExprList() {
    return PsiTreeUtil.getStubChildrenOfTypeAsList(this, LuaParenExpr.class);
  }

  @Override
  @NotNull
  public List<LuaTableExpr> getTableExprList() {
    return PsiTreeUtil.getStubChildrenOfTypeAsList(this, LuaTableExpr.class);
  }

}
