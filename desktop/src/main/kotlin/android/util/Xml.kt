package android.util

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

object Xml {
    fun newPullParser(): XmlPullParser = XmlPullParserFactory.newInstance().newPullParser()
}
