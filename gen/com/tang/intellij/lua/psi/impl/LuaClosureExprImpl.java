// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.tang.intellij.lua.psi.LuaTypes.*;
import com.tang.intellij.lua.stubs.LuaClosureExprStub;
import com.tang.intellij.lua.psi.*;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;

public class LuaClosureExprImpl extends LuaExprMixin<LuaClosureExprStub> implements LuaClosureExpr {

  public LuaClosureExprImpl(@NotNull LuaClosureExprStub stub, @NotNull IStubElementType<?, ?> nodeType) {
    super(stub, nodeType);
  }

  public LuaClosureExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public LuaClosureExprImpl(@NotNull LuaClosureExprStub stub, @NotNull IElementType type, @NotNull ASTNode node) {
    super(stub, type, node);
  }

  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitClosureExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public LuaFuncBody getFuncBody() {
    return notNullChild(PsiTreeUtil.getStubChildOfType(this, LuaFuncBody.class));
  }

  @Override
  @NotNull
  public List<LuaParamDef> getParamDefList() {
    return LuaPsiImplUtilKt.getParamDefList(this);
  }

  @Override
  @Nullable
  public ITy guessReturnType(@NotNull SearchContext context) {
    return LuaPsiImplUtilKt.guessReturnType(this, context);
  }

  @Override
  @NotNull
  public ITy guessParentType(@NotNull SearchContext context) {
    return LuaPsiImplUtilKt.guessParentType(this, context);
  }

  @Override
  @NotNull
  public LuaParamInfo[] getParams() {
    return LuaPsiImplUtilKt.getParams(this);
  }

}
