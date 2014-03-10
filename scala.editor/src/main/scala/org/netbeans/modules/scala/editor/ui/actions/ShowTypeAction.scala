/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2009 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2009 Sun Microsystems, Inc.
 */

package org.netbeans.modules.scala.editor.ui.actions

import java.awt.event.ActionEvent
import javax.swing.{JPopupMenu, JTextArea, JScrollPane}
import javax.swing.text.JTextComponent
import org.netbeans.modules.editor.NbEditorUtilities
import javax.swing.text.Document
import org.netbeans.editor.BaseAction
import org.openide.util.NbBundle
import org.openide.util.RequestProcessor
import org.netbeans.modules.scala.core.{ScalaGlobal, ScalaSourceFile}

/**
 *
 * @author Caoyuan Deng
 */
class ShowTypeAction extends BaseAction(NbBundle.getMessage(classOf[ShowTypeAction], "show-type"), 0) {

  var doc: Option[Document] = None

  override def isEnabled: Boolean = {
    true
  }

  def actionPerformed(evt: ActionEvent, comp: JTextComponent) {
    assert(comp ne null)
    comp.getDocument match {
      case null =>
      case doc => 
        RequestProcessor.getDefault post new Runnable {
          def run: Unit = {
            val dob = NbEditorUtilities getDataObject doc
            if (dob == null) return
            val fo = dob.getPrimaryFile
            val global = ScalaGlobal getGlobal fo
            val scalaSourceFile = ScalaSourceFile.sourceFileOf(fo)
            if (global == null) return
            
            val caretPos = comp.getCaretPosition
            val pos = global.rangePos(scalaSourceFile, caretPos, caretPos, caretPos)
            
            val response = new global.Response[global.Tree]
            global.askTypeAt(pos, response)
            response.get(1000) match {
              case Some(Left(tree)) =>
                popup(comp, describeTreeType(global, tree))
              case Some(Right(ex)) => popup(comp, ex.toString)
              case None => popup(comp, "Couldn't get info in time")
            }
          }
        }
    }
  }
  
  def describeTreeType(g: ScalaGlobal, t: ScalaGlobal#Tree): String = {
    import g._
    def describeType(t: ScalaGlobal#Type): String = {
      t match {
        case NoType => ""
        case tr@TypeRef(RefinedType(types, scope), sym, arg) =>
          //assume this is a type selection of the form A#B, so instead describe the type of B
          tr.toString + " ==> " +
          describeType(sym.tpe) + (if (arg.isEmpty) "" else arg.map(describeType).mkString("[", ", ", "]"))
        case TypeRef(pre, sym, arg) => 
          (if (pre != NoType) describeType(pre) + "." else "") + sym.nameString + 
          (if (arg.isEmpty) "" else arg.map(describeType).mkString("[", ", ", "]"))
        case RefinedType(types, scope) => types.map(describeType).mkString(" with ") + 
          (if (scope.isEmpty) "" else "{ " + scope.map(sym => g.show(sym) + ": " + describeType(sym.typeSignature)).mkString("; ") + " }")
        case SingleType(pre, ident) =>
          (if (pre != NoType) describeType(pre) + "." else "") + describeType(ident.tpe)
        case other => other.toString
      }
    }
    if (t == null || t.symbol == null) "No suitiable info available"
    else {
      t.symbol.toString + ": " + describeType(t.symbol.tpe.normalize) + "\n\n" +
      t.symbol.toString + ": " + describeType(t.tpe) + "\n\n" + 
      t.symbol.toString + ": " + describeType(t.tpe.typeSymbol.typeSignature) + "\n\n" + 
      g.show(t) + "\n\n" + g.showRaw(t)
    }
  }
  
  def popup(comp: JTextComponent, msg: String) {
    val awtRect = comp.modelToView(comp.getCaret.getDot)
    val popup = new JPopupMenu("Type")
    val textArea = new JTextArea(10, 60)
    textArea.setText(msg)
    popup.add(new JScrollPane(textArea))
    popup.show(comp, (awtRect.getX + awtRect.getWidth).toInt, (awtRect.getY + awtRect.getHeight).toInt)
  }
}

