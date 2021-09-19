// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.tang.intellij.lua.stubs.LuaPlaceholderStub;
import com.tang.intellij.lua.stubs.LuaExprPlaceStub;
import com.tang.intellij.lua.stubs.LuaNameExprStub;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.tang.intellij.lua.stubs.LuaLocalFuncDefStub;
import com.tang.intellij.lua.stubs.LuaTableExprStub;
import com.tang.intellij.lua.stubs.LuaUnaryExprStub;
import com.tang.intellij.lua.stubs.LuaLiteralExprStub;
import com.tang.intellij.lua.stubs.LuaClosureExprStub;
import com.tang.intellij.lua.stubs.LuaFuncDefStatStub;
import com.tang.intellij.lua.stubs.LuaClassMethodDefStatStub;
import com.tang.intellij.lua.stubs.LuaBinaryExprStub;
import com.tang.intellij.lua.stubs.LuaIndexExprStub;

public class LuaVisitor extends PsiElementVisitor {

  public void visitArgs(@NotNull LuaArgs o) {
    visitPsiElement(o);
  }

  public void visitAssignStat(@NotNull LuaAssignStat o) {
    visitStatement(o);
    // visitDeclaration(o);
    // visitTypeScope(o);
    // visitStatement(o);
  }

  public void visitBinaryExpr(@NotNull LuaBinaryExpr o) {
    visitExpression(o);
  }

  public void visitBinaryOp(@NotNull LuaBinaryOp o) {
    visitPsiElement(o);
  }

  public void visitBlock(@NotNull LuaBlock o) {
    visitDeclarationScope(o);
  }

  public void visitBreakStat(@NotNull LuaBreakStat o) {
    visitStatement(o);
  }

  public void visitCallExpr(@NotNull LuaCallExpr o) {
    visitExpression(o);
  }

  public void visitClassMethodDefStat(@NotNull LuaClassMethodDefStat o) {
    visitTypeMethod(o);
    // visitDeclaration(o);
    // visitTypeScope(o);
    // visitStatement(o);
  }

  public void visitClassMethodName(@NotNull LuaClassMethodName o) {
    visitPsiElement(o);
  }

  public void visitCloseAttribute(@NotNull LuaCloseAttribute o) {
    visitAttribute(o);
  }

  public void visitClosureExpr(@NotNull LuaClosureExpr o) {
    visitFuncBodyOwner(o);
    // visitExpression(o);
  }

  public void visitConstAttribute(@NotNull LuaConstAttribute o) {
    visitAttribute(o);
  }

  public void visitDoStat(@NotNull LuaDoStat o) {
    visitStatement(o);
    // visitIndentRange(o);
    // visitStatement(o);
  }

  public void visitEmptyStat(@NotNull LuaEmptyStat o) {
    visitStatement(o);
  }

  public void visitExprList(@NotNull LuaExprList o) {
    visitPsiElement(o);
  }

  public void visitExprStat(@NotNull LuaExprStat o) {
    visitStatement(o);
  }

  public void visitForAStat(@NotNull LuaForAStat o) {
    visitStatement(o);
    // visitParametersOwner(o);
    // visitLoop(o);
    // visitIndentRange(o);
    // visitDeclarationScope(o);
    // visitStatement(o);
  }

  public void visitForBStat(@NotNull LuaForBStat o) {
    visitStatement(o);
    // visitParametersOwner(o);
    // visitLoop(o);
    // visitIndentRange(o);
    // visitDeclarationScope(o);
    // visitStatement(o);
  }

  public void visitFuncBody(@NotNull LuaFuncBody o) {
    visitIndentRange(o);
    // visitDeclarationScope(o);
  }

  public void visitFuncDefStat(@NotNull LuaFuncDefStat o) {
    visitTypeMethod(o);
    // visitDeclaration(o);
    // visitTypeScope(o);
    // visitStatement(o);
  }

  public void visitGotoStat(@NotNull LuaGotoStat o) {
    visitStatement(o);
  }

  public void visitIfStat(@NotNull LuaIfStat o) {
    visitStatement(o);
    // visitIndentRange(o);
    // visitStatement(o);
  }

  public void visitIndexExpr(@NotNull LuaIndexExpr o) {
    visitPsiTypeMember(o);
    // visitExpression(o);
    // visitPsiNameIdentifierOwner(o);
  }

  public void visitLabelStat(@NotNull LuaLabelStat o) {
    visitStatement(o);
    // visitPsiNameIdentifierOwner(o);
    // visitStatement(o);
  }

  public void visitListArgs(@NotNull LuaListArgs o) {
    visitArgs(o);
  }

  public void visitLiteralExpr(@NotNull LuaLiteralExpr o) {
    visitExpression(o);
  }

  public void visitLocalDef(@NotNull LuaLocalDef o) {
    visitNamedElement(o);
    // visitPsiTypeGuessable(o);
    // visitPsiNameIdentifierOwner(o);
  }

  public void visitLocalDefStat(@NotNull LuaLocalDefStat o) {
    visitDeclaration(o);
    // visitDeclarationScope(o);
    // visitTypeScope(o);
    // visitStatement(o);
  }

  public void visitLocalFuncDefStat(@NotNull LuaLocalFuncDefStat o) {
    visitFuncBodyOwner(o);
    // visitDeclaration(o);
    // visitTypeScope(o);
    // visitPsiNameIdentifierOwner(o);
    // visitStatement(o);
  }

  public void visitNameExpr(@NotNull LuaNameExpr o) {
    visitModuleField(o);
    // visitExpression(o);
    // visitPsiNameIdentifierOwner(o);
  }

  public void visitParamDef(@NotNull LuaParamDef o) {
    visitNamedElement(o);
    // visitPsiTypeGuessable(o);
    // visitPsiNameIdentifierOwner(o);
  }

  public void visitParenExpr(@NotNull LuaParenExpr o) {
    visitExpression(o);
  }

  public void visitRepeatStat(@NotNull LuaRepeatStat o) {
    visitStatement(o);
    // visitLoop(o);
    // visitIndentRange(o);
    // visitDeclarationScope(o);
    // visitStatement(o);
  }

  public void visitReturnStat(@NotNull LuaReturnStat o) {
    visitStatement(o);
    // visitDeclaration(o);
    // visitStatement(o);
  }

  public void visitShebangLine(@NotNull LuaShebangLine o) {
    visitPsiElement(o);
  }

  public void visitSingleArg(@NotNull LuaSingleArg o) {
    visitArgs(o);
  }

  public void visitTableExpr(@NotNull LuaTableExpr o) {
    visitIndentRange(o);
    // visitExpression(o);
  }

  public void visitTableField(@NotNull LuaTableField o) {
    visitTypeField(o);
    // visitPsiNameIdentifierOwner(o);
    // visitCommentOwner(o);
    // visitTypeScope(o);
  }

  public void visitTableFieldSep(@NotNull LuaTableFieldSep o) {
    visitPsiElement(o);
  }

  public void visitUnaryExpr(@NotNull LuaUnaryExpr o) {
    visitExpression(o);
  }

  public void visitUnaryOp(@NotNull LuaUnaryOp o) {
    visitPsiElement(o);
  }

  public void visitVarList(@NotNull LuaVarList o) {
    visitExprList(o);
  }

  public void visitWhileStat(@NotNull LuaWhileStat o) {
    visitStatement(o);
    // visitLoop(o);
    // visitIndentRange(o);
    // visitStatement(o);
  }

  public void visitAttribute(@NotNull LuaAttribute o) {
    visitPsiElement(o);
  }

  public void visitDeclaration(@NotNull LuaDeclaration o) {
    visitPsiElement(o);
  }

  public void visitDeclarationScope(@NotNull LuaDeclarationScope o) {
    visitPsiElement(o);
  }

  public void visitExpression(@NotNull LuaExpression o) {
    visitPsiElement(o);
  }

  public void visitFuncBodyOwner(@NotNull LuaFuncBodyOwner o) {
    visitPsiElement(o);
  }

  public void visitIndentRange(@NotNull LuaIndentRange o) {
    visitPsiElement(o);
  }

  public void visitModuleField(@NotNull LuaModuleField o) {
    visitPsiElement(o);
  }

  public void visitNamedElement(@NotNull LuaNamedElement o) {
    visitPsiElement(o);
  }

  public void visitPsiTypeMember(@NotNull LuaPsiTypeMember o) {
    visitPsiElement(o);
  }

  public void visitStatement(@NotNull LuaStatement o) {
    visitPsiElement(o);
  }

  public void visitTypeField(@NotNull LuaTypeField o) {
    visitPsiElement(o);
  }

  public void visitTypeMethod(@NotNull LuaTypeMethod o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull LuaPsiElement o) {
    visitElement(o);
  }

}
