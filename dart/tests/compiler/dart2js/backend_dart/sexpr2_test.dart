// Copyright (c) 2014, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

/// Unittest test of the CPS ir generated by the dart2dart compiler.
library dart_backend.sexpr2_test;

import 'package:compiler/src/dart2jslib.dart';
import 'package:compiler/src/cps_ir/cps_ir_nodes_sexpr.dart';
import 'package:compiler/src/elements/elements.dart';
import 'package:expect/expect.dart';

import '../../../../pkg/analyzer2dart/test/test_helper.dart';
import '../../../../pkg/analyzer2dart/test/sexpr_data.dart';

import 'test_helper.dart';

main() {
  performTests(TEST_DATA, asyncTester, (TestSpec result) {
    return compilerFor(result.input).then((Compiler compiler) {
      void checkOutput(Element element, String expectedOutput) {
        expectedOutput = expectedOutput.trim();
        String output = compiler.irBuilder.getIr(element)
            .accept(new SExpressionStringifier()).trim();
        Expect.equals(expectedOutput, output,
            '\nInput:\n${result.input}\n'
            'Expected:\n$expectedOutput\n'
            'Actual:\n$output\n');
      }

      if (result.output is String) {
        checkOutput(compiler.mainFunction, result.output);
      } else {
        assert(result.output is Map<String, String>);
        result.output.forEach((String elementName, String output) {
          checkOutput(compiler.mainApp.localLookup(elementName), output);
        });
      }
    });
  });
}