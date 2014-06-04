// Copyright (c) 2012, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

#include "vm/bootstrap_natives.h"

#include "vm/code_patcher.h"
#include "vm/exceptions.h"
#include "vm/object.h"
#include "vm/stack_frame.h"

namespace dart {

DECLARE_FLAG(bool, warn_on_javascript_compatibility);

static void JSWarning(const char* msg) {
  DartFrameIterator iterator;
  iterator.NextFrame();  // Skip native call.
  StackFrame* caller_frame = iterator.NextFrame();
  ASSERT(caller_frame != NULL);
  const Code& caller_code = Code::Handle(caller_frame->LookupDartCode());
  ASSERT(!caller_code.IsNull());
  const uword caller_pc = caller_frame->pc();
  // Assume an unoptimized static call. Optimization was prevented.
  ICData& ic_data = ICData::Handle();
  CodePatcher::GetUnoptimizedStaticCallAt(caller_pc, caller_code, &ic_data);
  ASSERT(!ic_data.IsNull());
  // Report warning only if not already reported at this location.
  if (!ic_data.IssuedJSWarning()) {
    ic_data.SetIssuedJSWarning();
    Exceptions::JSWarning(caller_frame, "%s", msg);
  }
}


DEFINE_NATIVE_ENTRY(Identical_comparison, 2) {
  GET_NATIVE_ARGUMENT(Instance, a, arguments->NativeArgAt(0));
  GET_NATIVE_ARGUMENT(Instance, b, arguments->NativeArgAt(1));
  const bool is_identical = a.IsIdenticalTo(b);
  if (FLAG_warn_on_javascript_compatibility) {
    if (!is_identical) {
      if (a.IsString()) {
        if (String::Cast(a).Equals(b)) {
          JSWarning("strings that are equal are also identical");
        }
      } else if (a.IsInteger()) {
        if (b.IsDouble()) {
          const int64_t a_value = Integer::Cast(a).AsInt64Value();
          const double b_value = Double::Cast(b).value();
          if (a_value == floor(b_value)) {
            JSWarning("integer value and integral double value that are equal "
                      "are also identical");
          }
        }
      } else if (a.IsDouble()) {
        if (b.IsInteger()) {
          const double a_value = Double::Cast(a).value();
          const int64_t b_value = Integer::Cast(b).AsInt64Value();
          if (floor(a_value) == b_value) {
            JSWarning("integral double value and integer value that are equal "
                      "are also identical");
          }
        }
      }
    }
  }
  return Bool::Get(is_identical).raw();
}

}  // namespace dart
