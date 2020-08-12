package example.frontend

import com.raquo.airstream.signal.Signal
import com.raquo.laminar.api.L._
import org.scalajs.dom


object Client {
  case class Editor private (node: Element, text: Signal[String], init: () => Unit)
  object Editor {
    import typings.monacoEditor.mod.editor.create

    def start = {
      val container = div(
        width := "100%",
        margin := "50px",
        height := "500px",
        border := "1px solid grey"
      )

      val opts = typings.monacoEditor.mod.editor.IStandaloneEditorConstructionOptions()

      opts.language = "scala"
      opts.value = "hello!"

      val text = Var("")
      
      val init = () => {
        val monacoEditor = create(container.ref, opts)

        monacoEditor.onKeyUp(_ => text.set(monacoEditor.getValue))

        ()
      }
      

      new Editor(container, text.signal, init )
    }
  }

  def app = {
    val editor = Editor.start

    div(
      div("Editor"),
      editor.node,
      div("Code", div(child.text <-- editor.text))
    ) -> editor

  }

  def main(args: Array[String]): Unit = {
    documentEvents.onDomContentLoaded.foreach { _ =>
      val (appNode, editor) = app

      render(dom.document.getElementById("appContainer"), appNode)

      editor.init()
    }(unsafeWindowOwner)
  }
}
