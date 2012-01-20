/*
 * a * Copyright 2012 Dart project authors.
 * 
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.dart.tools.ui.swtbot.dialog;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.waits.ICondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.TimeoutException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper for driving native dialogs
 */
class NativeDialogHelper {

  protected final SWTWorkbenchBot bot;
  protected final Display display;
  protected final Shell originalShell;

  private static final int MODIFIER_MASK = SWT.ALT | SWT.SHIFT | SWT.CTRL | SWT.COMMAND;
  private static final int[] MODIFIERS = new int[] {SWT.ALT, SWT.SHIFT, SWT.CTRL, SWT.COMMAND};

  public NativeDialogHelper(SWTWorkbenchBot bot) {
    this.bot = bot;
    this.display = bot.getDisplay();
    this.originalShell = getActiveShell();
  }

  /**
   * Ensure that the native shell opened by this helper has been closed
   */
  protected void ensureNativeShellClosed() {
    SWTBotShell activeShell = null;
    try {
      activeShell = bot.activeShell();
    } catch (WidgetNotFoundException e) {
      //$FALL-THROUGH$
    }
    if (activeShell == null) {
      System.out.println("Active shell is null or not found... attempting to close native dialog");
      typeKeyCode(SWT.ESC);
    }
  }

  /**
   * Answer the currently active shell. This can be called from a non-UI thread and handles the case
   * where the SWT main event loop gets blocked by SWT dialog event loop
   */
  protected Shell getActiveShell() {
    final CountDownLatch latch = new CountDownLatch(1);
    // Try for up to 5 seconds
    for (int i = 0; i < 10; i++) {
      final Shell[] result = new Shell[1];
      // Queue a new runnable each time
      // because a new event processing loop is created when a dialog opens
      // thus the old runnable may never get served until after the dialog closes
      display.asyncExec(new Runnable() {

        @Override
        public void run() {
          result[0] = display.getActiveShell();
          latch.countDown();
        }
      });
      try {
        if (latch.await(500, TimeUnit.MILLISECONDS)) {
          return result[0];
        }
      } catch (InterruptedException e) {
        //$FALL-THROUGH$
      }
    }
    throw new TimeoutException("Failed to determine active shell");
  }

  /**
   * Post key click events (key down followed by key up) for the specified character. To type an
   * entire string, see {@link #typeText(String)}. If you want to queue events for characters with
   * accelerators such as {@link WT#CTRL} | 's', call {@link #typeKeyCode(int)} rather than this
   * method.
   * 
   * @param ch the character without accelerators
   */
  protected void typeChar(final char ch) {
    boolean shift = needsShift(ch);
    if (shift) {
      postKeyCodeDown(SWT.SHIFT);
    }
    postCharDown(ch);
    postCharUp(ch);
    if (shift) {
      postKeyCodeUp(SWT.SHIFT);
    }
  }

  /**
   * Post key click events (key down followed by key up) for the specified keyCode. All uppercase
   * characters (e.g. 'T') are converted to lower case (e.g. 't') thus <code>keyCode('T')</code> is
   * equivalent to <code>keyCode('t')</code>. Also see {@link #typeChar(char)} and
   * {@link #typeText(String)}
   * 
   * @param keyCode the code for the key to be posted such as {@link SWT#HOME}, {@link SWT#CTRL} |
   *          't', {@link SWT#SHIFT} | {@link SWT#END}
   */
  protected void typeKeyCode(final int keyCode) {
    postModifierKeysDown(keyCode);
    int unmodified = keyCode - (keyCode & MODIFIER_MASK);

    // Key code characters have the SWT.KEYCODE_BIT bit set
    // whereas unicode characters do not
    if ((unmodified & SWT.KEYCODE_BIT) != 0) {
      postKeyCodeDown(unmodified);
      postKeyCodeUp(unmodified);
    } else {
      char ch = (char) unmodified;
      if (Character.isLetter(ch)) {
        ch = Character.toLowerCase(ch);
      }
      postCharDown(ch);
      postCharUp(ch);
    }

    postModifierKeysUp(keyCode);
  }

  /**
   * Post key click events (key down followed by key up) for the specified text
   */
  protected void typeText(String text) {
    if (text != null) {
      for (int i = 0; i < text.length(); i++) {
        typeChar(text.charAt(i));
      }
    }
  }

  /**
   * Wait for the original shell to loose focus, and assume that when that happens the native shell
   * then has focus.
   */
  protected void waitForNativeShellShowing() {
    bot.waitUntil(new ICondition() {

      @Override
      public String getFailureMessage() {
        return "Timed out waiting for native dialog";
      }

      @Override
      public void init(SWTBot bot) {
      }

      @Override
      public boolean test() throws Exception {
        return originalShell != getActiveShell();
      }
    });
    bot.sleep(1000);
  }

  /**
   * Determine if this key requires a shift to dispatch the keyStroke.
   * 
   * @param keyCode - the key in question
   * @return true if a shift event is required.
   */
  private boolean needsShift(char keyCode) {

    if (keyCode >= 62 && keyCode <= 90) {
      return true;
    }
    if (keyCode >= 123 && keyCode <= 126) {
      return true;
    }
    if (keyCode >= 33 && keyCode <= 43 && keyCode != 39) {
      return true;
    }
    if (keyCode >= 94 && keyCode <= 95) {
      return true;
    }
    if (keyCode == 58 || keyCode == 60 || keyCode == 62) {
      return true;
    }

    return false;
  }

  /**
   * Post key down event for the specified character
   * 
   * @param ch the character
   */
  private void postCharDown(final char ch) {
    Event event;
    event = new Event();
    event.type = SWT.KeyDown;
    event.character = ch;
    postEvent(event);
  }

  /**
   * Post key up event for the specified character
   * 
   * @param ch the character
   */
  private void postCharUp(final char ch) {
    Event event;
    event = new Event();
    event.type = SWT.KeyUp;
    event.character = ch;
    postEvent(event);
  }

  /**
   * Post the specified event to the OS event queue then sleep for 1/100 second to give the UI
   * thread a chance to get control and process the event
   * 
   * @param event the event (not <code>null</code>)
   */
  private void postEvent(final Event event) {
    final boolean[] success = new boolean[1];
    display.syncExec(new Runnable() {
      @Override
      public void run() {
        success[0] = display.post(event);
      }
    });
    if (!success[0]) {
      throw new RuntimeException("Failed to post event: " + event);
    }
    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
      //$FALL-THROUGH$
    }
  }

  /**
   * Post key down event for the specified keyCode
   * 
   * @param keyCode the code for the key down to be queued such as {@link WT#HOME}, {@link WT#CTRL},
   *          {@link WT#SHIFT}, {@link WT#END}
   */
  private void postKeyCodeDown(int keyCode) {
    Event event;
    event = new Event();
    event.type = SWT.KeyDown;
    event.keyCode = keyCode;
    postEvent(event);
  }

  /**
   * Post key up event for the specified keyCode
   * 
   * @param keyCode the code for the key down to be queued such as {@link WT#HOME}, {@link WT#CTRL},
   *          {@link WT#SHIFT}, {@link WT#END}
   */
  private void postKeyCodeUp(int keyCode) {
    Event event;
    event = new Event();
    event.type = SWT.KeyUp;
    event.keyCode = keyCode;
    postEvent(event);
  }

  /**
   * Examine the accelerator bits to determine if any modifier keys (Shift, Alt, Control, Command)
   * are specified and post zero or more key down events for those modifier keys.
   * 
   * @param accelerator the accelerator that may specify zero or more modifier keys<br/>
   *          ({@link WT#SHIFT} , {@link WT#CTRL}, ...)
   */
  private void postModifierKeysDown(int accelerator) {
    for (int i = 0; i < MODIFIERS.length; i++) {
      int mod = MODIFIERS[i];
      if ((accelerator & mod) == mod) {
        postKeyCodeDown(mod);
      }
    }
  }

  /**
   * Examine the accelerator bits to determine if any modifier keys (Shift, Alt, Control, Command)
   * are specified and post zero or more key up events for those modifier keys.
   * 
   * @param accelerator the accelerator that may specify zero or more modifier keys<br/>
   *          ({@link WT#SHIFT} , {@link WT#CTRL}, ...)
   */
  private void postModifierKeysUp(int accelerator) {
    for (int i = MODIFIERS.length - 1; i >= 0; i--) {
      int mod = MODIFIERS[i];
      if ((accelerator & mod) == mod) {
        postKeyCodeUp(mod);
      }
    }
  }

}
