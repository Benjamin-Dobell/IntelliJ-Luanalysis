/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.ty

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.Processor
import com.intellij.util.io.StringRef
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.psi.LuaDocGeneralTy
import com.tang.intellij.lua.comment.psi.LuaDocGenericDef
import com.tang.intellij.lua.comment.psi.LuaDocTableDef
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.psi.search.LuaClassInheritorsSearch
import com.tang.intellij.lua.psi.search.LuaShortNamesManager
import com.tang.intellij.lua.search.ProjectSearchContext
import com.tang.intellij.lua.search.PsiSearchContext
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.*

interface ITyClass : ITyResolvable {
    val className: String
    val varName: String

    var superClass: ITy?
    var aliasName: String?
    var params: Array<TyGenericParameter>?
    var signatures: Array<IFunSignature>?

    fun lazyInit(searchContext: SearchContext)
    fun processAlias(processor: Processor<String>): Boolean

    fun recoverAlias(context: SearchContext, aliasSubstitutor: TyAliasSubstitutor): ITy {
        return this
    }

    override fun willResolve(context: SearchContext): Boolean {
        lazyInit(context)
        return !isAnonymous && !isGlobal
    }

    override fun resolve(context: SearchContext, genericArgs: Array<out ITy>?): ITy {
        return if (willResolve(context)) {
            val scopedType = context.element?.let {
                LuaScopedTypeTree.get(it.containingFile).findName(context, it, className)?.type
            }

            if (scopedType != null) {
                scopedType
            } else {
                val aliasDef = LuaShortNamesManager.getInstance(context.project).findAlias(context, className)

                if (aliasDef != null) {
                    val aliasTy = aliasDef.type

                    if (genericArgs != null) {
                        TyGeneric(genericArgs, aliasTy)
                    } else {
                        aliasTy
                    }
                } else {
                    this
                }
            }
        } else {
            this
        }
    }
}

fun ITyClass.isVisibleInScope(project: Project, contextTy: ITy, visibility: Visibility): Boolean {
    if (visibility == Visibility.PUBLIC)
        return true

    TyUnion.each(contextTy) {
        if (it is ITyClass) {
            if (it == this || (
                            visibility == Visibility.PROTECTED
                            && LuaClassInheritorsSearch.isClassInheritFrom(GlobalSearchScope.projectScope(project), project, className, it.className))
            ) {
                return true
            }
        }
    }

    return false
}

abstract class TyClass(override val className: String,
                       override var params: Array<TyGenericParameter>? = null,
                       override val varName: String = "",
                       override var superClass: ITy? = null,
                       override var signatures: Array<IFunSignature>? = null
) : Ty(TyKind.Class), ITyClass {

    final override var aliasName: String? = null

    private var _lazyInitialized: Boolean = false

    override fun equals(other: Any?): Boolean {
        return other is ITyClass && other.className == className && other.flags == flags
    }

    override fun equals(other: ITy, context: SearchContext): Boolean {
        if (this === other) {
            return true
        }

        Ty.resolve(this, context).let {
            if (it !== this) {
                return it.equals(other, context)
            }
        }

        val resolvedOther = Ty.resolve(other, context)

        if (this === resolvedOther) {
            return true
        }

        if (resolvedOther is ITyClass) {
            lazyInit(context)
            resolvedOther.lazyInit(context)

            if (resolvedOther.className == className && resolvedOther.flags == flags) {
                return true
            }
        }

        if (isShape(context) && resolvedOther.isShape(context)) {
            return contravariantOf(resolvedOther, context, 0)
                    && resolvedOther.contravariantOf(this, context, 0)
        }

        return false
    }

    override fun hashCode(): Int {
        return className.hashCode()
    }

    override fun isShape(searchContext: SearchContext): Boolean {
        val resolved = Ty.resolve(this, searchContext)

        if (resolved !== this) {
            return resolved.isShape(searchContext)
        }

        lazyInit(searchContext)
        return super<Ty>.isShape(searchContext)
    }

    override fun processAlias(processor: Processor<String>): Boolean {
        val alias = aliasName
        if (alias == null || alias == className)
            return true
        if (!processor.process(alias))
            return false
        return true
    }

    override fun processMembers(context: SearchContext, deep: Boolean, process: (ITy, LuaClassMember) -> Boolean): Boolean {
        lazyInit(context)

        val clazzName = className
        val project = context.project

        val manager = LuaShortNamesManager.getInstance(project)
        val members = mutableListOf<LuaClassMember>()
        members.addAll(manager.getClassMembers(context, clazzName))

        processAlias { alias ->
            val classMembers = manager.getClassMembers(context, alias)
            members.addAll(classMembers)
        }

        for (member in members) {
            ProgressManager.checkCanceled()

            if (!process(this, member)) {
                return false
            }
        }

        // super
        if (deep) {
            val memberNames = mutableListOf<String>()
            val memberIndexTys = mutableListOf<ITy>()

            for (member in members) {
                val memberName = member.name

                if (memberName != null) {
                    memberNames.add(memberName)
                } else {
                    member.guessIndexType(context)?.let {
                        memberIndexTys.add(it)
                    }
                }
            }

            return processSuperClasses(this, context) {
                it.processMembers(context, false) { _, superMember ->
                    val superMemberName = superMember.name

                    val memberOverridden = if (superMemberName != null) {
                        memberNames.contains(superMemberName)
                    } else {
                        superMember.guessIndexType(context)?.let { superMemberIndexTy ->
                            memberIndexTys.find { memberIndexTy ->
                                memberIndexTy === superMemberIndexTy || memberIndexTy.contravariantOf(superMemberIndexTy, context, 0)
                            } != null
                        } ?: false
                    }

                    if (memberOverridden) {
                        true
                    } else {
                        process(this, superMember)
                    }
                }
            }
        }

        return true
    }

    override fun processSignatures(context: SearchContext, processor: Processor<IFunSignature>): Boolean {
        lazyInit(context)

        signatures?.let {
            for (signature in it) {
                if (!processor.process(signature)) {
                    return false
                }
            }
        }

        return true
    }

    override fun processMember(context: SearchContext, name: String, deep: Boolean, process: (ITy, LuaClassMember) -> Boolean): Boolean {
        return LuaShortNamesManager.getInstance(context.project).processMember(context, this, name, true, deep, process)
    }

    override fun processIndexer(context: SearchContext, indexTy: ITy, exact: Boolean, deep: Boolean, process: (ITy, LuaClassMember) -> Boolean): Boolean {
        return LuaShortNamesManager.getInstance(context.project).processIndexer(context, this, indexTy, exact, true, deep, process)
    }

    override fun accept(visitor: ITyVisitor) {
        visitor.visitClass(this)
    }

    override fun lazyInit(searchContext: SearchContext) {
        if (!_lazyInitialized && !searchContext.isDumb) {
            _lazyInitialized = true
            doLazyInit(searchContext)
        }
    }

    open fun doLazyInit(searchContext: SearchContext) {
        if (aliasName == null) {
            val classDef = LuaPsiTreeUtil.findClass(className, searchContext)
            if (classDef != null) {
                val tyClass = classDef.type
                aliasName = tyClass.aliasName
                superClass = tyClass.superClass
                params = tyClass.params
                flags = tyClass.flags
                signatures = tyClass.signatures
            }
        }
    }

    override fun getSuperClass(context: SearchContext): ITy? {
        lazyInit(context)
        return superClass
    }

    override fun getParams(context: SearchContext): Array<TyGenericParameter>? {
        lazyInit(context)
        return params
    }

    override fun substitute(substitutor: ITySubstitutor): ITy {
        return substitutor.substitute(this)
    }

    override fun contravariantOf(other: ITy, context: SearchContext, flags: Int): Boolean {
        lazyInit(context)

        val resolved = Ty.resolve(this, context)

        if (resolved !== this) {
            return resolved.contravariantOf(other, context, flags)
        } else {
            return super.contravariantOf(other, context, flags)
        }
    }

    companion object {
        // for _G
        val G: TyClass = createSerializedClass(Constants.WORD_G)

        fun createGlobalType(name: String): ITy {
            return createSerializedClass(getGlobalTypeName(name), null, name, null, null, null, TyFlags.GLOBAL)
        }

        fun createGlobalType(nameExpr: LuaNameExpr): ITy {
            return createGlobalType(nameExpr.name)
        }

        fun createSelfType(classTy: ITyClass): TyClass {
            val tyName = getSelfType(classTy)
            return createSerializedClass(tyName, null, Constants.WORD_SELF, classTy, null, null, TyFlags.ANONYMOUS)
        }
    }
}

class TyPsiDocClass(val tagClass: LuaDocTagClass) : TyClass(
        tagClass.name,
        tagClass.genericDefList.map { TyGenericParameter(it) }.toTypedArray(),
        "",
        tagClass.superClassRef?.let { Ty.create(it) },
        tagClass.overloads
) {
    init {
        aliasName = tagClass.aliasName
        this.flags = if (tagClass.isShape) TyFlags.SHAPE else 0
    }

    override fun doLazyInit(searchContext: SearchContext) {}
}

open class TySerializedClass(name: String,
                             params: Array<TyGenericParameter>? = null,
                             varName: String = name,
                             superClass: ITy? = null,
                             signatures: Array<IFunSignature>? = null,
                             alias: String? = null,
                             flags: Int = 0)
    : TyClass(name, params, varName, superClass, signatures) {
    init {
        aliasName = alias
        this.flags = flags
    }

    override fun recoverAlias(context: SearchContext, aliasSubstitutor: TyAliasSubstitutor): ITy {
        if (this.isAnonymous || this.isGlobal)
            return this
        val alias = LuaShortNamesManager.getInstance(context.project).findAlias(context, className)
        return alias?.type?.substitute(aliasSubstitutor) ?: this
    }

    override fun contravariantOf(other: ITy, context: SearchContext, flags: Int): Boolean {
        if (isUnknown) {
            // Same behaviour as TyUnknown
            return other !is TyMultipleResults
        }

        return super.contravariantOf(other, context, flags)
    }

    override fun guessMemberType(name: String, searchContext: SearchContext): ITy? {
        val memberTy = super.guessMemberType(name, searchContext)

        if (memberTy == null && isUnknown && LuaSettings.instance.isUnknownIndexable) {
            return Ty.UNKNOWN
        }

        return memberTy
    }

    override fun guessIndexerType(indexTy: ITy, searchContext: SearchContext, exact: Boolean): ITy? {
        val memberTy = super.guessIndexerType(indexTy, searchContext, exact)

        if (memberTy == null && isUnknown && LuaSettings.instance.isUnknownIndexable) {
            return Ty.UNKNOWN
        }

        return memberTy
    }
}

class TyLazyClass(name: String, val psi: PsiElement? = null) : TySerializedClass(name, null) {
    override fun doLazyInit(searchContext: SearchContext) {
        val context = if (psi != null) {
            PsiSearchContext(psi)
        } else {
            ProjectSearchContext(searchContext.project)
        }

        super.doLazyInit(context)
    }
}

fun createSerializedClass(name: String,
                          params: Array<TyGenericParameter>? = null,
                          varName: String = name,
                          superClass: ITy? = null,
                          signatures: Array<IFunSignature>? = null,
                          alias: String? = null,
                          flags: Int = 0): TyClass {
    val list = name.split("|")
    if (list.size == 3) {
        val type = list[0].toInt()
        if (type == 10) {
            return TySerializedDocTable(name)
        }
    }

    return TySerializedClass(name, params, varName, superClass, signatures, alias, flags)
}

fun createTableGenericFromMembers(ty: ITy, context: SearchContext): ITyGeneric {
    val isEmpty = ty.processMembers(context) { _, _ -> false }

    if (isEmpty) {
        return TyGeneric(arrayOf(Ty.UNKNOWN, Ty.UNKNOWN), Ty.TABLE)
    }

    var keyType: ITy? = null
    var elementType: ITy? = null

    ty.processMembers(context) { prefixTy, classMember ->
        val name = classMember.name
        val indexType = classMember.indexType

        if (classMember is LuaTableField) {
            val exprList = classMember.expressionList

            if (exprList.size == 2) {
                keyType = context.withIndex(0) {
                    exprList[0].guessType(context)?.let {
                        TyUnion.union(keyType, it, context)
                    } ?: Ty.UNKNOWN
                }
                elementType = context.withIndex(0) {
                    exprList[1].guessType(context)?.let {
                        TyUnion.union(elementType, it, context)
                    } ?: Ty.UNKNOWN
                }
            } else if (exprList.size == 1) {
                if (name != null) {
                    keyType = TyUnion.union(keyType, TyPrimitiveLiteral.getTy(TyPrimitiveKind.String, name), context)
                } else {
                    keyType = TyUnion.union(keyType, Ty.NUMBER, context)
                }

                elementType = exprList[0].guessType(context)?.let {
                    TyUnion.union(elementType, it, context)
                } ?: Ty.UNKNOWN
            }
        } else if (classMember is LuaIndexExpr) {
            val idExpr = classMember.idExpr

            if (name != null) {
                keyType = TyUnion.union(keyType, TyPrimitiveLiteral.getTy(TyPrimitiveKind.String, name), context)
                elementType = prefixTy.guessMemberType(name, context)?.let {
                    TyUnion.union(elementType, it, context)
                } ?: Ty.UNKNOWN
            } else if (idExpr != null) {
                val indexTy = idExpr.guessType(context) ?: Ty.UNKNOWN
                keyType = TyUnion.union(keyType, indexTy, context)
                elementType = prefixTy.guessIndexerType(indexTy, context)?.let {
                    TyUnion.union(elementType, it, context)
                } ?: Ty.UNKNOWN
            } else {
                keyType = Ty.UNKNOWN
                elementType = Ty.UNKNOWN
            }
        } else if (name != null) {
            keyType = TyUnion.union(keyType, TyPrimitiveLiteral.getTy(TyPrimitiveKind.String, name), context)
            elementType = classMember.guessType(context)?.let {
                TyUnion.union(elementType, it, context)
            } ?: Ty.UNKNOWN
        } else if (indexType != null) {
            keyType = TyUnion.union(keyType, indexType.getType(), context)
            elementType = classMember.guessType(context)?.let {
                TyUnion.union(elementType, it, context)
            } ?: Ty.UNKNOWN
        } else {
            keyType = Ty.UNKNOWN
            elementType = Ty.UNKNOWN
        }

        keyType?.isUnknown == false || elementType?.isUnknown == false
    }

    return TyGeneric(arrayOf(keyType ?: Ty.UNKNOWN, elementType ?: Ty.UNKNOWN), Ty.TABLE)
}

fun getTableTypeName(table: LuaTableExpr): String {
    val stub = table.stub
    if (stub != null)
        return stub.tableTypeName

    val fileName = table.containingFile.name
    return "$fileName@(${table.node.startOffset})table"
}

fun getAnonymousTypeName(variableDef: LuaPsiElement): String {
    return "${variableDef.node.startOffset}@${variableDef.containingFile.name}"
}

fun getSelfType(classTy: ITyClass): String {
    return "${classTy.className}#${Constants.WORD_SELF}"
}

fun getGlobalTypeName(text: String): String {
    return if (text == Constants.WORD_G) text else "$$text"
}

fun getGlobalTypeName(nameExpr: LuaNameExpr): String {
    return getGlobalTypeName(nameExpr.name)
}

class TyTable(val table: LuaTableExpr) : TyClass(getTableTypeName(table)) {
    init {
        this.flags = TyFlags.ANONYMOUS_TABLE or TyFlags.SHAPE
    }

    override fun toString(): String = displayName

    override fun doLazyInit(searchContext: SearchContext) = Unit
}

fun getDocTableTypeName(table: LuaDocTableDef): String {
    val stub = table.stub
    if (stub != null)
        return stub.className

    val fileName = table.containingFile.name
    return "10|$fileName|${table.node.startOffset}"
}

private fun getDocTableImplicitParams(table: LuaDocTableDef): Array<TyGenericParameter>? {
    val params = mutableListOf<TyGenericParameter>()

    table.tableFieldList.forEach { field ->
        val value = field.valueType

        if (value is LuaDocGeneralTy) {
            val name = value.typeRef.name
            val scopedType = SearchContext.withDumb(value.project, null) {
                (LuaScopedTypeTree.get(value.containingFile).findName(it, value, name) as? LuaDocGenericDef)?.type
            }

            if (scopedType != null && !params.contains(scopedType)) {
                params.add(scopedType)
            }
        }
    }

    return if (params.isNotEmpty()) {
        params.toTypedArray()
    } else null
}

class TyDocTable(val table: LuaDocTableDef) : TyClass(getDocTableTypeName(table), getDocTableImplicitParams(table)) {
    init {
        this.flags = TyFlags.SHAPE
    }

    override fun doLazyInit(searchContext: SearchContext) {}

    override fun processMembers(context: SearchContext, deep: Boolean, process: (ITy, LuaClassMember) -> Boolean): Boolean {
        table.tableFieldList.forEach {
            if (!process(this, it)) {
                return false
            }
        }
        return true
    }

    override fun processMember(context: SearchContext, name: String, deep: Boolean, process: (ITy, LuaClassMember) -> Boolean): Boolean {
        return table.tableFieldList.firstOrNull { it.name == name }?.let {
            process(this, it)
        } ?: true
    }

    override fun processIndexer(context: SearchContext, indexTy: ITy, exact: Boolean, deep: Boolean, process: (ITy, LuaClassMember) -> Boolean): Boolean {
        var narrowestClassMember: LuaClassMember? = null
        var narrowestIndexTy: ITy? = null

        table.tableFieldList.forEach {
            val candidateIndexerTy = it.guessIndexType(context)

            if ((!exact && candidateIndexerTy?.contravariantOf(indexTy, context, TyVarianceFlags.STRICT_UNKNOWN) == true)
                || candidateIndexerTy == indexTy) {
                if (narrowestIndexTy?.contravariantOf(candidateIndexerTy, context, TyVarianceFlags.STRICT_UNKNOWN) != false) {
                    narrowestClassMember = it
                    narrowestIndexTy = candidateIndexerTy
                }
            }
        }

        return narrowestClassMember?.let { process(this, it) } ?: true
    }

    // TODO: TyDocTable should implement this. However, there's no sensible way
    //       to do so at present because LuaClassMember inherits from PsiElement.
    /*override fun substitute(substitutor: ITySubstitutor): ITy {
    }*/
}

class TySerializedDocTable(name: String) : TySerializedClass(name) {
    override fun recoverAlias(context: SearchContext, aliasSubstitutor: TyAliasSubstitutor): ITy {
        return this
    }
}

object TyClassSerializer : TySerializer<ITyClass>() {
    override fun deserializeTy(flags: Int, stream: StubInputStream): ITyClass {
        val className = stream.readName()
        val params = stream.readGenericParamsNullable()
        val varName = stream.readName()
        val superClass = stream.readTyNullable()
        val signatures = stream.readSignatureNullable()
        val aliasName = stream.readName()
        return createSerializedClass(StringRef.toString(className),
                params,
                StringRef.toString(varName),
                superClass,
                signatures,
                StringRef.toString(aliasName),
                flags)
    }

    override fun serializeTy(ty: ITyClass, stream: StubOutputStream) {
        stream.writeName(ty.className)
        stream.writeGenericParamsNullable(ty.params)
        stream.writeName(ty.varName)
        stream.writeTyNullable(ty.superClass)
        stream.writeSignaturesNullable(ty.signatures)
        stream.writeName(ty.aliasName)
    }
}
