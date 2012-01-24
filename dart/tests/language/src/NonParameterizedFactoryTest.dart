// Copyright (c) 2011, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

interface Interface<T> default Factory<T> {
  Interface();
  Interface.withArg(T value);
}

class Factory<T> {
  factory Interface() {
    return null;
  }

  factory Interface.withArg(value) {
    return null;
  }
}

main() {
  new Interface();
  new Interface.withArg(4);
}
