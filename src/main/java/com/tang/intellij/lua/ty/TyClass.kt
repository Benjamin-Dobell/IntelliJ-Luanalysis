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
import com.tang.intellij.lua.comment.psi.LuaDocTableDef
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
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
                LuaScopedTypeTree.get(it.containingFile)?.findName(context, it, className)?.type
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

private fun equalToShape(context: SearchContext, target: ITy, source: ITy): Boolean {
    if (source !is ITyClass || source is ITyPrimitive) {
        return false
    }

    val sourceSubstitutor = source.getMemberSubstitutor(context)
    val targetSubstitutor = target.getMemberSubstitutor(context)

    var isEqual = true
    var targetMemberCount = 0

    target.processMembers(context, true) { _, targetMember ->
        targetMemberCount++

        val indexTy = targetMember.guessIndexType(context)

        val targetMemberTy = targetMember.guessType(context).let {
            if (it == null) {
                return@processMembers true
            }

            if (targetSubstitutor != null) {
                it.substitute(context, targetSubstitutor)
            } else it
        }

        val sourceMember = if (indexTy != null) {
            source.findIndexer(context, indexTy, true)
        } else {
            targetMember.name?.let { source.findMember(context, it) }
        }

        if (sourceMember == null) {
            isEqual = TyUnion.find(targetMemberTy, TyNil::class.java) == null
            return@processMembers isEqual
        }

        val sourceMemberTy = (sourceMember.guessType(context) ?: Primitives.UNKNOWN).let {
            if (sourceSubstitutor != null) it.substitute(context, sourceSubstitutor) else it
        }

        if (!targetMemberTy.equals(context, sourceMemberTy)) {
            isEqual = false
            return@processMembers false
        }

        true
    }

    if (!isEqual) {
        return false
    }

    var sourceMemberCount = 0

    source.processMembers(context, true) { _, sourceMember ->
        sourceMemberCount++ < targetMemberCount
    }

    return targetMemberCount == sourceMemberCount
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

    override fun equals(context: SearchContext, other: ITy): Boolean {
        if (this === other) {
            return true
        }

        Ty.resolve(context, this).let {
            if (it !== this) {
                return it.equals(context, other)
            }
        }

        val resolvedOther = Ty.resolve(context, other)

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

        if (flags and TyVarianceFlags.NON_STRUCTURAL == 0 && isShape(context) && resolvedOther.isShape(context)) {
            return equalToShape(context, this, resolvedOther)
        }

        return false
    }

    override fun hashCode(): Int {
        return className.hashCode()
    }

    override fun isShape(context: SearchContext): Boolean {
        val resolved = Ty.resolve(context, this)

        if (resolved !== this) {
            return resolved.isShape(context)
        }

        lazyInit(context)
        return super<Ty>.isShape(context)
    }

    override fun processAlias(processor: Processor<String>): Boolean {
        val alias = aliasName
        if (alias == null || alias == className)
            return true
        if (!processor.process(alias))
            return false
        return true
    }

    override fun processMembers(context: SearchContext, deep: Boolean, process: ProcessTypeMember): Boolean {
        lazyInit(context)

        val clazzName = className
        val project = context.project

        val manager = LuaShortNamesManager.getInstance(project)
        val members = mutableListOf<LuaPsiTypeMember>()
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

            return processSuperClasses(context, this) {
                it.processMembers(context, false) { _, superMember ->
                    val superMemberName = superMember.name

                    val memberOverridden = if (superMemberName != null) {
                        memberNames.contains(superMemberName)
                    } else {
                        superMember.guessIndexType(context)?.let { superMemberIndexTy ->
                            memberIndexTys.find { memberIndexTy ->
                                memberIndexTy === superMemberIndexTy || memberIndexTy.contravariantOf(context, superMemberIndexTy, 0)
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

    override fun processMember(context: SearchContext, name: String, deep: Boolean, process: ProcessTypeMember): Boolean {
        return LuaShortNamesManager.getInstance(context.project).processMember(context, this, name, true, deep, process)
    }

    override fun processIndexer(context: SearchContext, indexTy: ITy, exact: Boolean, deep: Boolean, process: ProcessTypeMember): Boolean {
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
            val classDef = LuaPsiTreeUtil.findClass(searchContext, className)
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

    override fun getSuperType(context: SearchContext): ITy? {
        lazyInit(context)
        return superClass
    }

    override fun getParams(context: SearchContext): Array<TyGenericParameter>? {
        lazyInit(context)
        return params
    }

    override fun substitute(context: SearchContext, substitutor: ITySubstitutor): ITy {
        return substitutor.substitute(context, this)
    }

    override fun contravariantOf(context: SearchContext, other: ITy, flags: Int): Boolean {
        lazyInit(context)

        val resolved = Ty.resolve(context, this)

        if (resolved !== this) {
            return resolved.contravariantOf(context, other, flags)
        } else {
            return super.contravariantOf(context, other, flags)
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
            val tyName = getSelfClassName(classTy)
            return createSerializedClass(tyName, null, Constants.WORD_SELF, classTy, null, null, TyFlags.ANONYMOUS)
        }

        fun createConcreteGenericParameter(genericParam: TyGenericParameter): TyClass {
            val tyName = getConcreteGenericParameterName(genericParam)
            return createSerializedClass(tyName, null, genericParam.varName, genericParam, null, null, TyFlags.ANONYMOUS)
        }
    }
}

class TyPsiDocClass(val tagClass: LuaDocTagClass) : TyClass(
        tagClass.name,
        tagClass.genericDefList.map { TyGenericParameter(it) }.toTypedArray(),
        "",
        tagClass.superClass?.getType(),
        tagClass.overloads
) {
    init {
        aliasName = tagClass.aliasName
        this.flags = if (tagClass.isShape) TyFlags.SHAPE else 0
    }

    override fun willResolve(context: SearchContext): Boolean {
        return false // Nothing to resolve
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
        return alias?.type?.substitute(context, aliasSubstitutor) ?: this
    }

    override fun contravariantOf(context: SearchContext, other: ITy, flags: Int): Boolean {
        if (isUnknown) {
            // Same behaviour as TyUnknown
            return other !is TyMultipleResults
        }

        return super.contravariantOf(context, other, flags)
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

fun createTableGenericFromMembers(context: SearchContext, ty: ITy): ITyGeneric {
    val isEmpty = ty.processMembers(context) { _, _ -> false }

    if (isEmpty) {
        return TyGeneric(arrayOf(Primitives.UNKNOWN, Primitives.UNKNOWN), Primitives.TABLE)
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
                        TyUnion.union(context, keyType, it)
                    } ?: Primitives.UNKNOWN
                }
                elementType = context.withIndex(0) {
                    exprList[1].guessType(context)?.let {
                        TyUnion.union(context, elementType, it)
                    } ?: Primitives.UNKNOWN
                }
            } else if (exprList.size == 1) {
                if (name != null) {
                    keyType = TyUnion.union(context, keyType, TyPrimitiveLiteral.getTy(TyPrimitiveKind.String, name))
                } else {
                    keyType = TyUnion.union(context, keyType, Primitives.NUMBER)
                }

                elementType = exprList[0].guessType(context)?.let {
                    TyUnion.union(context, elementType, it)
                } ?: Primitives.UNKNOWN
            }
        } else if (classMember is LuaIndexExpr) {
            val idExpr = classMember.idExpr

            if (name != null) {
                keyType = TyUnion.union(context, keyType, TyPrimitiveLiteral.getTy(TyPrimitiveKind.String, name))
                elementType = prefixTy.guessMemberType(context, name)?.let {
                    TyUnion.union(context, elementType, it)
                } ?: Primitives.UNKNOWN
            } else if (idExpr != null) {
                val indexTy = idExpr.guessType(context) ?: Primitives.UNKNOWN
                keyType = TyUnion.union(context, keyType, indexTy)
                elementType = prefixTy.guessIndexerType(context, indexTy)?.let {
                    TyUnion.union(context, elementType, it)
                } ?: Primitives.UNKNOWN
            } else {
                keyType = Primitives.UNKNOWN
                elementType = Primitives.UNKNOWN
            }
        } else if (name != null) {
            keyType = TyUnion.union(context, keyType, TyPrimitiveLiteral.getTy(TyPrimitiveKind.String, name))
            elementType = classMember.guessType(context)?.let {
                TyUnion.union(context, elementType, it)
            } ?: Primitives.UNKNOWN
        } else if (indexType != null) {
            keyType = TyUnion.union(context, keyType, indexType.getType())
            elementType = classMember.guessType(context)?.let {
                TyUnion.union(context, elementType, it)
            } ?: Primitives.UNKNOWN
        } else {
            keyType = Primitives.UNKNOWN
            elementType = Primitives.UNKNOWN
        }

        keyType?.isUnknown == false || elementType?.isUnknown == false
    }

    return TyGeneric(arrayOf(keyType ?: Primitives.UNKNOWN, elementType ?: Primitives.UNKNOWN), Primitives.TABLE)
}

fun getTableTypeName(table: LuaTableExpr): String {
    val stub = table.stub
    if (stub != null)
        return stub.tableTypeName

    val id = table.containingFile.getFileIdentifier()
    return "$id@(${table.node.startOffset})table"
}

fun getAnonymousTypeName(variableDef: LuaPsiElement): String {
    return "${variableDef.node.startOffset}@${variableDef.containingFile.getFileIdentifier()}"
}

private val SUFFIXED_CLASS_NAME_REGEX = Regex("#[a-zA-Z]+$")

fun isSuffixedClassName(className: String): Boolean {
    return SUFFIXED_CLASS_NAME_REGEX.find(className) != null
}

fun isSuffixedClass(classTy: ITyClass): Boolean {
    return isSuffixedClassName(classTy.className)
}

fun getSuffixlessClassName(className: String): String {
    return className.replace(SUFFIXED_CLASS_NAME_REGEX, "")
}

fun getSuffixlessClassName(classTy: ITyClass): String {
    return getSuffixlessClassName(classTy.className)
}

private const val CLASS_NAME_SUFFIX_SELF = "#self"

fun getSelfClassName(classTy: ITyClass): String {
    val className = classTy.className
    return if (className.endsWith(CLASS_NAME_SUFFIX_SELF)) {
        className
    } else {
        className + CLASS_NAME_SUFFIX_SELF
    }
}

fun isSelfClass(classTy: ITyClass): Boolean {
    return classTy.className.endsWith(CLASS_NAME_SUFFIX_SELF)
}

private const val GENERIC_PARAMETER_NAME_SUFFIX_CONCRETE = "#concrete"

fun getConcreteGenericParameterName(genericParam: TyGenericParameter): String {
    val genericName = genericParam.varName
    return if (genericName.endsWith(GENERIC_PARAMETER_NAME_SUFFIX_CONCRETE)) {
        genericName
    } else {
        genericName + GENERIC_PARAMETER_NAME_SUFFIX_CONCRETE
    }
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

    override fun willResolve(context: SearchContext): Boolean {
        return false
    }

    override fun doLazyInit(searchContext: SearchContext) = Unit

    override fun processMembers(context: SearchContext, deep: Boolean, process: ProcessTypeMember): Boolean {
        if (!context.isDumb) {
            return super.processMembers(context, deep, process)
        }

        table.tableFieldList.forEach {
            if (!process(this, it)) {
                return false
            }
        }

        return true
    }

    override fun processMember(context: SearchContext, name: String, deep: Boolean, process: ProcessTypeMember): Boolean {
        if (!context.isDumb) {
            return super.processMember(context, name, deep, process)
        }

        return table.tableFieldList.firstOrNull {
            val fieldName = it.name
            if (fieldName != null) {
                fieldName == name
            } else {
                it.indexType?.getType()?.let {
                    (it is ITyPrimitive && it.primitiveKind == TyPrimitiveKind.String)
                            || (it is TyPrimitiveLiteral && it.primitiveKind == TyPrimitiveKind.String && it.value == name)
                } ?: false
            }
        }?.let {
            process(this, it)
        } ?: true
    }

    override fun processIndexer(context: SearchContext, indexTy: ITy, exact: Boolean, deep: Boolean, process: ProcessTypeMember): Boolean {
        if (!context.isDumb) {
            return super.processIndexer(context, indexTy, exact, deep, process)
        }

        var narrowestTypeMember: TypeMember? = null
        var narrowestIndexTy: ITy? = null

        table.tableFieldList.forEach { field ->
            val candidateIndexerTy = field.guessIndexType(context) ?: field.name?.let {
                TyPrimitiveLiteral.getTy(TyPrimitiveKind.String, it)
            }

            if ((!exact && candidateIndexerTy?.contravariantOf(context, indexTy, TyVarianceFlags.STRICT_UNKNOWN) == true)
                    || candidateIndexerTy == indexTy) {
                if (narrowestIndexTy?.contravariantOf(context, candidateIndexerTy, TyVarianceFlags.STRICT_UNKNOWN) != false) {
                    narrowestTypeMember = field
                    narrowestIndexTy = candidateIndexerTy
                }
            }
        }

        return narrowestTypeMember?.let { process(this, it) } ?: true
    }
}

fun getDocTableTypeName(table: LuaDocTableDef): String {
    val stub = table.stub
    if (stub != null)
        return stub.className

    val id = table.containingFile.getFileIdentifier()
    return "10|$id|${table.node.startOffset}"
}

fun getSubstitutedDocTableTypeName(table: LuaDocTableDef): String {
    // TODO: This is very bad... and useless.
    return "${getDocTableTypeName(table)}${Math.random()}"
}


open class TyDocTable(val table: LuaDocTableDef, name: String = getDocTableTypeName(table)) : TyClass(name) {
    init {
        this.flags = TyFlags.SHAPE
    }

    override fun willResolve(context: SearchContext): Boolean {
        return false
    }

    override fun doLazyInit(searchContext: SearchContext) = Unit

    override fun processMembers(context: SearchContext, deep: Boolean, process: ProcessTypeMember): Boolean {
        table.tableFieldList.forEach {
            if (!process(this, it)) {
                return false
            }
        }
        return true
    }

    override fun processMember(context: SearchContext, name: String, deep: Boolean, process: ProcessTypeMember): Boolean {
        return table.tableFieldList.firstOrNull {
            val fieldName = it.name
            if (fieldName != null) {
                fieldName == name
            } else {
                it.indexType?.getType()?.let {
                    (it is ITyPrimitive && it.primitiveKind == TyPrimitiveKind.String)
                        || (it is TyPrimitiveLiteral && it.primitiveKind == TyPrimitiveKind.String && it.value == name)
                } ?: false
            }
        }?.let {
            process(this, it)
        } ?: true
    }

    override fun processIndexer(context: SearchContext, indexTy: ITy, exact: Boolean, deep: Boolean, process: ProcessTypeMember): Boolean {
        var narrowestTypeMember: TypeMember? = null
        var narrowestIndexTy: ITy? = null

        table.tableFieldList.forEach { field ->
            val candidateIndexerTy = field.guessIndexType(context) ?: field.name?.let {
                TyPrimitiveLiteral.getTy(TyPrimitiveKind.String, it)
            }

            if ((!exact && candidateIndexerTy?.contravariantOf(context, indexTy, TyVarianceFlags.STRICT_UNKNOWN) == true)
                || candidateIndexerTy == indexTy) {
                if (narrowestIndexTy?.contravariantOf(context, candidateIndexerTy, TyVarianceFlags.STRICT_UNKNOWN) != false) {
                    narrowestTypeMember = field
                    narrowestIndexTy = candidateIndexerTy
                }
            }
        }

        return narrowestTypeMember?.let { process(this, it) } ?: true
    }

    override fun substitute(context: SearchContext, substitutor: ITySubstitutor): ITy {
        return TySubstitutedDocTable(this, substitutor)
    }
}

class TySubstitutedDocTable(docTable: TyDocTable, val substitutor: ITySubstitutor): TyDocTable(docTable.table, getSubstitutedDocTableTypeName(docTable.table)) {
    init {
        // TODO: This is a hack. This just convinces the LuaClassMemberIndex to look for members using the parent doc
        //       table's name. Instead we should implement processMember/Indexer and call LuaClassMemberIndex with the
        //       parent doc table's name. However, "sub-classes" of substituted doc tables would call into
        //       LuaClassMemberIndex, and it presently doesn't call back out to processMember/Indexer on parent types.
        //       See notes on LuaClassMemberIndex.
        superClass = docTable
    }

    override fun getMemberSubstitutor(context: SearchContext): ITySubstitutor? {
        return TyChainSubstitutor.chain(super.getMemberSubstitutor(context), substitutor)
    }
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
