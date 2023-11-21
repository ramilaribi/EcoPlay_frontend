package com.example.ecoplay_front.view

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ecoplay_front.R
import com.example.ecoplay_front.adapter.CommentsAdapter
import com.example.ecoplay_front.apiService.ChallengeApi
import com.example.ecoplay_front.fragments.AddCommentDialogFragment
import com.example.ecoplay_front.model.Comment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.Date


class ActivityCommunity : AppCompatActivity(), CommentsAdapter.OnItemClickListener, AddCommentDialogFragment.AddCommentDialogListener {
    private lateinit var fabAddComment: FloatingActionButton
    private lateinit var challengeApi: ChallengeApi
    private var challengeId: String? = null
    private lateinit var commentsAdapter: CommentsAdapter
    private lateinit var recyclerView: RecyclerView

    private lateinit var progressBar: ProgressBar
    private lateinit var progressBar2: ProgressBar



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_community)

        recyclerView = findViewById(R.id.recycler_view_comments)
        commentsAdapter = CommentsAdapter(mutableListOf(), this) // Pass 'this' as the listener here
        progressBar = findViewById(R.id.progressBar)
        progressBar2 = findViewById(R.id.progressBar2)

        recyclerView.adapter = commentsAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        val availablePostsTextView = findViewById<TextView>(R.id.textView_available_posts)
        styleTextView(availablePostsTextView, "Available ", "posts")

        challengeId?.let { fetchComments(it) }

        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.1.115:9001/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        challengeApi = retrofit.create(ChallengeApi::class.java)

        challengeId = intent.getStringExtra("challengeId")

        Log.d("lookforid", "Challenge ID: $challengeId")

        challengeId?.let {
            fetchComments(it)
        }



        fabAddComment = findViewById<FloatingActionButton>(R.id.fab_add_comment).apply {
            setOnClickListener {
                showAddCommentDialog()
            }
        }
    }

    private fun fetchComments(challengeId: String?) {
        progressBar.visibility = View.VISIBLE
        progressBar2.visibility = View.VISIBLE
        val call = challengeApi.getComments(challengeId)
        Log.d("ActivityCommunity", "Fetching comments from URL: ${call.request().url}")

        call.enqueue(object : Callback<List<Comment>> {
            override fun onResponse(call: Call<List<Comment>>, response: Response<List<Comment>>) {
                if (response.isSuccessful) {


                    val commentsList = response.body() ?: emptyList()

                    Log.d("lookforcomments", "Comments fetched: $commentsList")
                    commentsAdapter.setComments(commentsList)
                } else {
                    Log.e("ActivityCommunity", "Error getting comments: ${response.errorBody()?.string()}")
                }

                progressBar.visibility = View.GONE
                progressBar2.visibility = View.GONE
            }

            override fun onFailure(call: Call<List<Comment>>, t: Throwable) {
                Log.e("ActivityCommunity", "Failure getting comments", t)

            }
        })
    }


    private fun showAddCommentDialog() {
        val dialog = AddCommentDialogFragment().apply {
            setAddCommentListener(this@ActivityCommunity)
        }
        dialog.show(supportFragmentManager, AddCommentDialogFragment.TAG)
    }


    override fun onAttemptToAddComment(title: String, description: String, imageUri: Uri?, onResult: (Boolean) -> Unit) {

        postComment(title, description, imageUri) { success ->
            onResult(success)
        }
    }

    private fun postComment(title: String, description: String, imageUri: Uri?, onResult: (Boolean) -> Unit) {
        progressBar.visibility = View.VISIBLE
        progressBar2.visibility = View.VISIBLE

        val userIdBody = RequestBody.create("text/plain".toMediaTypeOrNull(), "507f1f77bcf86cd799439011")
        val titleBody = RequestBody.create("text/plain".toMediaTypeOrNull(), title)
        val descriptionBody = RequestBody.create("text/plain".toMediaTypeOrNull(), description)

        val imagePart: MultipartBody.Part? = imageUri?.let { uri ->
            val inputStream = contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val tempFile = File.createTempFile("upload", ".jpg", cacheDir).apply {
                    deleteOnExit()
                }
                tempFile.outputStream().use { fileOut ->
                    stream.copyTo(fileOut)
                }
                val requestFile = RequestBody.create("image/*".toMediaTypeOrNull(), tempFile)
                MultipartBody.Part.createFormData("image", tempFile.name, requestFile)
            }
        }

        challengeId?.let {
            challengeApi.postComment(it, userIdBody, titleBody, descriptionBody, imagePart).enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    if (response.isSuccessful) {
                        progressBar.visibility = View.GONE
                        progressBar2.visibility = View.GONE
                        Log.d("commentpostedyes", "Comment posted successfully")
                        challengeId?.let { fetchComments(it)
                            onResult(true)
                        }

                    } else {
                        val errorBody = response.errorBody()?.string()
                        try {
                            val jsonObject = JSONObject(errorBody)
                            val errorMessage = jsonObject.getString("error")
                            Log.e("error mte3na", "Error posting comment: ${response.errorBody()?.string()}")
//
//                            if (errorMessage.contains("inappropriate content")) {
//                                showAlert("Inappropriate Content", "Please keep it cool. No bad words allowed ^^")
//                            } else {
//                                Log.e("ActivityCommunity", "Error posting comment: $errorBody")
//                            }

                            onResult(false)


                            progressBar.visibility = View.GONE
                            progressBar2.visibility = View.GONE
                        } catch (e: Exception) {
                            Log.e("ActivityCommunity", "Error parsing error response: $errorBody")
                        }
                    }
                }


                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    progressBar2.visibility = View.GONE
                    onResult(false)

                    if (t is IOException) {
                        Log.d("ActivityCommunity", "Image URI: $imageUri")
                        Log.e("ActivityCommunity", "Network error: ${t.message}")
                    } else {
                        Log.e("ActivityCommunity", "Error posting comment: ${t.message}")
                    }
                }
            })
        }
    }

    private fun styleTextView(textView: TextView, partOne: String, partTwo: String) {
        val spannableString = SpannableString(partOne + partTwo)

        spannableString.setSpan(
            ForegroundColorSpan(Color.BLACK),
            0,
            partOne.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        spannableString.setSpan(
            ForegroundColorSpan(Color.parseColor("#44F1A6")),
            partOne.length,
            spannableString.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        textView.text = spannableString
    }

    private fun showAlert(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }


    override fun onItemClick(comment: Comment) {
        // Start the RateActivity with the comment ID passed as an extra
        val intent = Intent(this, RateActivity::class.java).apply {
            putExtra("commentId", comment.id)
        }
        startActivity(intent)
    }

}