/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.lint;

import static com.android.tools.lint.checks.CheckResultDetector.CHECK_PERMISSION;

import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.LintIdeQuickFix;
import com.android.tools.idea.lint.common.ReplaceCallFix;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLintUseCheckPermissionInspection extends AndroidLintInspectionBase {
  public AndroidLintUseCheckPermissionInspection() {
    super(AndroidBundle.message("android.lint.inspections.use.check.permission"), CHECK_PERMISSION);
  }

  @Override
  @NotNull
  public LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                         @NotNull PsiElement endElement,
                                         @NotNull String message,
                                         @Nullable LintFix fixData) {
    String suggested = LintFix.getData(fixData, String.class);
    if (suggested != null) {
      return new LintIdeQuickFix[]{
        new ReplaceCallFix(suggested)
      };
    }

    return super.getQuickFixes(startElement, endElement, message, fixData);
  }
}
