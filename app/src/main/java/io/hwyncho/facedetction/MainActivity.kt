package io.hwyncho.facedetction

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import javax.net.ssl.HttpsURLConnection

class MainActivity : AppCompatActivity() {
    private var imageURL: String = ""

    private var imageCrop: Bitmap? = null
    private var imageDetect: Bitmap? = null

    private var resultCrop: String? = null
    private var resultDetect: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val editURL: EditText = findViewById<EditText>(R.id.edit_url)
        val progressBar: ProgressBar = findViewById<ProgressBar>(R.id.image_progress)
        val imageView: ImageView = findViewById<ImageView>(R.id.image_view)
        val btnGet: Button = findViewById<Button>(R.id.btn_get)
        val btnAnalyze: Button = findViewById<Button>(R.id.btn_analyze)

        btnGet.setOnClickListener { view ->
            progressBar.visibility = View.VISIBLE

            imageURL = editURL.text.toString()

            val cropThread: Thread = object : Thread() {
                override fun run() {
                    resultCrop = callCropAPI(imageURL)
                    if (resultCrop != null) {
                        Log.v("HWYNCHO_VERBOSE", resultCrop.toString())

                        var jsonObject: JSONObject = JSONObject(resultCrop)
                        imageURL = jsonObject.get("thumbnail_image_url").toString()

                        val conn: HttpURLConnection = URL(imageURL).openConnection() as HttpURLConnection
                        conn.doInput = true
                        conn.connect()
                        imageCrop = BitmapFactory.decodeStream(conn.inputStream)
                    } else {
                        Log.e("HWYNCHO_VERBOSE", "resultCrop is null")
                    }
                }
            }

            try {
                cropThread.start()
                cropThread.join()

                editURL.setText(imageURL)
                imageView.setImageBitmap(imageCrop)

                progressBar.visibility = View.INVISIBLE
            } catch (e: Exception) {
                Log.e("HWYNCHO_ERROR", e.toString())
            }

            resultCrop = null
        }

        btnAnalyze.setOnClickListener { view ->
            progressBar.visibility = View.VISIBLE

            val detectThread: Thread = object : Thread() {
                override fun run() {
                    try {
                        resultDetect = callDetectAPI(imageURL)
                        Log.v("HWYNCHO_VERBOSE", resultDetect.toString())
                    } catch (e: Exception) {
                        Log.e("HWYNCHO_ERROR", e.toString())
                    }
                }
            }

            try {
                detectThread.start()
                detectThread.join()

                progressBar.visibility = View.VISIBLE

                var jsonObject: JSONObject = JSONObject(resultDetect)
                var resultObject = jsonObject.get("result") as JSONObject

                var imgWidth = resultObject.get("width") as Int
                var imgHeight = resultObject.get("height") as Int

                var imgTemp: Bitmap = Bitmap.createBitmap(imgWidth, imgHeight, imageCrop!!.config)
                val imgCanvas = Canvas(imgTemp)
                imgCanvas.drawBitmap(imageCrop!!, 0f, 0f, null)

                var faceArray: JSONArray = resultObject.get("faces") as JSONArray
                for (i in 0 until faceArray.length()) {
                    val face = faceArray.get(i) as JSONObject
                    val attrs: JSONObject = face.get("facial_attributes") as JSONObject

                    val maleScore: Double = (attrs.get("gender") as JSONObject).get("male") as Double
                    val femaleScore: Double = (attrs.get("gender") as JSONObject).get("female") as Double
                    val gender: String = if (maleScore > femaleScore) {
                        if (resources.configuration.locales.get(0).language == "ko") {
                            "남자"
                        } else {
                            "Male"
                        }
                    } else {
                        if (resources.configuration.locales.get(0).language == "ko") {
                            "여자"
                        } else {
                            "Female"
                        }
                    }

                    val age: Double = attrs.get("age") as Double

                    val info: String = if (resources.configuration.locales.get(0).language == "ko") {
                        "$gender / ${age.toInt()}세"
                    } else {
                        "$gender / ${age.toInt()} years"
                    }

                    val x = face.get("x") as Double
                    val y = face.get("y") as Double
                    val w = face.get("w") as Double
                    val h = face.get("h") as Double

                    val xLeft = imgWidth * x
                    val yTop = imgHeight * y
                    val xRight = xLeft + (imgWidth * w)
                    val yBottom = yTop + (imgHeight * h)

                    val rectangle = Paint()
                    rectangle.color = if (i % 2 == 0) {
                        ContextCompat.getColor(applicationContext, R.color.colorPrimary)
                    } else {
                        ContextCompat.getColor(applicationContext, R.color.colorAccent)
                    }
                    rectangle.style = Paint.Style.STROKE
                    rectangle.strokeWidth = 4f
                    rectangle.isAntiAlias = true
                    imgCanvas.drawRect(xLeft.toFloat(), yTop.toFloat(), xRight.toFloat(), yBottom.toFloat(), rectangle)

                    var text = Paint()
                    text.color = Color.WHITE
                    text.style = Paint.Style.FILL_AND_STROKE
                    text.isAntiAlias = true
                    text.textAlign = Paint.Align.LEFT
                    text.textSize = 24f
                    imgCanvas.drawText(info, xLeft.toFloat(), yBottom.toFloat() - 2, text)
                }

                imageDetect = imgTemp
                imageView.setImageDrawable(BitmapDrawable(resources, imageDetect))

                progressBar.visibility = View.INVISIBLE
            } catch (e: Exception) {
                Log.e("HWYNCHO_ERROR", e.toString())
            }

            resultDetect = null
        }
    }

    companion object {
        @Throws(Exception::class)
        fun callCropAPI(imageUrl: String): String? {
            val url = URL("https://kapi.kakao.com/v1/vision/thumbnail/crop")
            val params = "image_url=$imageUrl&width=500&height=500"

            val conn: HttpsURLConnection

            try {
                conn = url.openConnection() as HttpsURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "")
                conn.doOutput = true

                val writer: OutputStreamWriter = OutputStreamWriter(conn.outputStream)
                writer.write(params)
                writer.flush()

                val responseCode = conn.responseCode
                val isr: InputStreamReader = if (responseCode == 200) {
                    InputStreamReader(conn.inputStream)
                } else {
                    InputStreamReader(conn.errorStream)
                }

                val reader: BufferedReader = BufferedReader(isr)
                val buffer = StringBuffer()

                var line: String? = null
                line = reader.readLine()
                while (line != null) {
                    buffer.append(line)
                    line = reader.readLine()
                }

                reader.close()
                isr.close()
                writer.close()

                return buffer.toString()
            } catch (e: Exception) {
                Log.e("HWYNCHO_ERROR", e.toString())
                return null
            }
        }

        @Throws(Exception::class)
        fun callDetectAPI(imageUrl: String): String? {
            val url = URL("https://kapi.kakao.com/v1/vision/face/detect")
            val params = "image_url=$imageUrl"

            val conn: HttpsURLConnection

            try {
                conn = url.openConnection() as HttpsURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "")
                conn.doOutput = true

                val writer: OutputStreamWriter = OutputStreamWriter(conn.outputStream)
                writer.write(params)
                writer.flush()

                val responseCode = conn.responseCode
                val isr: InputStreamReader = if (responseCode == 200) {
                    InputStreamReader(conn.inputStream)
                } else {
                    InputStreamReader(conn.errorStream)
                }

                val reader: BufferedReader = BufferedReader(isr)
                val buffer = StringBuffer()

                var line: String? = null
                line = reader.readLine()
                while (line != null) {
                    buffer.append(line)
                    line = reader.readLine()
                }

                reader.close()
                isr.close()
                writer.close()

                return buffer.toString()
            } catch (e: Exception) {
                Log.e("HWYNCHO_ERROR", e.toString())
                return null
            }
        }
    }
}
