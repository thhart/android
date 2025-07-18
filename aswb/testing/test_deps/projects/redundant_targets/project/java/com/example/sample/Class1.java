/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.sample;

import com.example.lib.LibClass;
import com.example.top_level_lib_1.Lib1;

/** Class1 test class */
public class Class1 {
  private final LibClass libClass = new LibClass("hello");
  public final Lib1 lib1 = Lib1.createTest();

  public void method() {
    System.out.println(libClass.getClass());
    System.out.println(lib1.getClass());
  }
}
