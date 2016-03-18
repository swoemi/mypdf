package myparser

import java.io.{File, RandomAccessFile}

/**
  * Created by ThomasE on 22.02.2016.
  */
class PDFile(path : String) {

  val parser = new PDFParser(path)
  parser.goto(parser.findXref())
  val (xrefTable, root) = createXref()
  val catalog = dereference(root)
  val pagesRoot = dereference(catalog.get("Pages"))
  val numberOfPages = pagesRoot.get("Count").toInt.toInt

  /**
    * Creates the xref table and the pages root object.
    * The position of the parser at the beginning is at the keyword 'xref'
    * @return the xref table and the pages root object
    */
  def createXref() : (Map[Int,Long], PDFObject) = {
    parser.nextToken() // consume 'xref'
    var xrefTable = Map[Int,Long]()
    val start = parser.nextToken().asInstanceOf[Long].toInt
    val length = parser.nextToken().asInstanceOf[Long].toInt
    Range(start, start+length).foreach(i => {
      xrefTable += (i -> parser.nextToken().asInstanceOf[Long])
      parser.nextToken()
      parser.nextToken()
    })
    if(!("trailer".equals(parser.nextToken().asInstanceOf[String]))) {
      throw new Exception("Keyword 'trailer' expected")
    }
    val trailer = parser.parseObject().asInstanceOf[PDFDict]
    var rootRef = trailer.get("Root")
    val prev = trailer.get("Prev")
    if(prev != null) {
      parser.goto(prev.asInstanceOf[PDFInteger].value.asInstanceOf[Long])
      val (newXref, rootObject) = createXref()
      newXref.foreach(i => xrefTable += i)
      if(rootRef == null) {
        rootRef = rootObject
      }
    }
    return (xrefTable, rootRef)
  }

  def getObject(ref: PDFRef): PDFObject = {
    parser.goto(xrefTable(ref.id))
    return parser.parseObject()
  }

  def getObject(id: Int): PDFObject = {
    parser.goto(xrefTable(id))
    return parser.parseObject()
  }

  def dereference(obj: PDFObject): PDFObject = obj match {
    case PDFRef(id, rev) => dereference(getObject(id))
    case _ => obj
  }

  def getStreamContent(streamRef : PDFRef) : Array[Byte] = {
    parser.goto(xrefTable(streamRef.id))
    return parser.getContent
  }


  def getPage(page: Int): PDFPage = {

    // todo inherit resources

    def huntDown(pages: PDFObject, _start: Int): PDFPage = {
      if (pages.get("Type").value().equals("Page")) {
        return new PDFPage(this, pages)
      }
      var start = _start
      val kids = dereference(pages.get("Kids")).asInstanceOf[PDFList]
      for (i <- 0 until kids.list.length) {
        val kid = dereference(kids.get(i))
        var count = 1
        val countItem = kid.get("Count")
        if (countItem != null) {
          count = countItem.toInt().toInt
        }
        if (start + count > page) {
          return huntDown(kid, start)
        }
        start += count
      }
      return null
    }
    return huntDown(pagesRoot, 0)
  }
}

class PDFPage(file_ : PDFile, obj_ : PDFObject) {

  val file = file_
  val obj = obj_
  var fontMap = Map[String, PDFFont]()

  def getContent(): Array[Byte] = {
    def getReferencedContent(streamRef : PDFRef): Array[Byte] = {
      file.parser.goto(file.xrefTable(streamRef.id))
      return file.parser.getContent
    }
    val contents = obj.get("Contents")
    val content : Array[Byte] = contents match {
      case PDFRef(id, rev) =>
        val streamRef = obj.get("Contents").asInstanceOf[PDFRef]
        return getReferencedContent(streamRef)
      case PDFList() => throw new Exception("Listed 'content' not yet implemented")
    }
    return null
  }

  def registerFonts(): Unit = {
    val resources = file.dereference(obj.get("Resources"))
    val fonts = resources.get("Font").asInstanceOf[PDFDict]
    fonts.map.keys.foreach(f => fontMap += (f -> new PDFFont(file,file.dereference(fonts.map(f).asInstanceOf[PDFRef]))))
  }

  def printContent(): Unit = {
    getContent().foreach(b => print(b.toChar))
  }
}





class DataBuffer(path : String) {

  val ra = new RandomAccessFile(new File(path), "r")
  var position = 0L;

  def read(): Byte = {
    val n = ra.read()
    if(n == -1) {
      throw new Exception("Reached End of File!")
    }
    position += 1
    return n.toByte
  }

  def seek(): Byte = {
    val n = ra.read()
    if(n == -1) {
      throw new Exception("Reached End of File!")
    }
    ra.seek(position)
    return n.toByte
  }

  def gotoEnd() : Unit = {
    goto(ra.length())
  }

  def goto(_position : Long): Unit = {
    position = _position
    ra.seek(position)
  }

  def read(bytes : Array[Byte]): Unit = {
    ra.read(bytes)
    position += bytes.length
  }

  def rewind(): Unit = {
    goto(position - 1)
  }
}

object Main {
  def main(args : Array[String]): Unit = {
    var file = new PDFile("C:\\Users\\thomase\\Desktop\\gui\\doc1.pdf")
    // var file = new PDFile("C:\\Users\\thomase\\Documents\\samplepdf\\preisliste\\d2.pdf")
    val page = file.getPage(0)
    // page.printContent()
    page.registerFonts()
    page.fontMap.keys.foreach(i => {
      val font = page.fontMap(i)
      font.toFile("C:/Users/thomase/font/" + i.toString + ".ttf")
    })
  }
}