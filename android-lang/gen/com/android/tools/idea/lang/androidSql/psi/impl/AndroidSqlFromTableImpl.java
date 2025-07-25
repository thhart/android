/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// ATTENTION: This file has been automatically generated from androidSql.bnf. Do not edit it manually.

package com.android.tools.idea.lang.androidSql.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.android.tools.idea.lang.androidSql.psi.*;
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlTable;

public class AndroidSqlFromTableImpl extends ASTWrapperPsiElement implements AndroidSqlFromTable {

  public AndroidSqlFromTableImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull AndroidSqlVisitor visitor) {
    visitor.visitFromTable(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AndroidSqlVisitor) accept((AndroidSqlVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public AndroidSqlDatabaseName getDatabaseName() {
    return findChildByClass(AndroidSqlDatabaseName.class);
  }

  @Override
  @NotNull
  public AndroidSqlDefinedTableName getDefinedTableName() {
    return findNotNullChildByClass(AndroidSqlDefinedTableName.class);
  }

  @Override
  @Nullable
  public AndroidSqlTableAliasName getTableAliasName() {
    return findChildByClass(AndroidSqlTableAliasName.class);
  }

  @Override
  @Nullable
  public PsiElement getBacktickLiteral() {
    return findChildByType(BACKTICK_LITERAL);
  }

  @Override
  @Nullable
  public PsiElement getBracketLiteral() {
    return findChildByType(BRACKET_LITERAL);
  }

  @Override
  @Nullable
  public PsiElement getDoubleQuoteStringLiteral() {
    return findChildByType(DOUBLE_QUOTE_STRING_LITERAL);
  }

  @Override
  @Nullable
  public PsiElement getIdentifier() {
    return findChildByType(IDENTIFIER);
  }

  @Override
  @Nullable
  public PsiElement getSingleQuoteStringLiteral() {
    return findChildByType(SINGLE_QUOTE_STRING_LITERAL);
  }

  @Override
  public @Nullable AndroidSqlTable getSqlTable() {
    return PsiImplUtil.getSqlTable(this);
  }

}
