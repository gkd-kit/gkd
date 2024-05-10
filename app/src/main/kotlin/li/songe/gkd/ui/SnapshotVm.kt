package li.songe.gkd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.call.body
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.data.GithubPoliciesAsset
import li.songe.gkd.data.RpcError
import li.songe.gkd.data.Snapshot
import li.songe.gkd.db.DbSet
import li.songe.gkd.debug.SnapshotExt.getSnapshotZipFile
import li.songe.gkd.util.FILE_UPLOAD_URL
import li.songe.gkd.util.LoadStatus
import li.songe.gkd.util.client
import li.songe.gkd.util.launchTry
import javax.inject.Inject


@HiltViewModel
class SnapshotVm @Inject constructor() : ViewModel() {
    val snapshotsState = DbSet.snapshotDao.query()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uploadStatusFlow = MutableStateFlow<LoadStatus<GithubPoliciesAsset>?>(null)
    var uploadJob: Job? = null

    fun uploadZip(snapshot: Snapshot) {
        uploadJob = viewModelScope.launchTry(Dispatchers.IO) {
            val zipFile = getSnapshotZipFile(snapshot.id)
            uploadStatusFlow.value = LoadStatus.Loading()
            try {
                val response =
                    client.submitFormWithBinaryData(url = FILE_UPLOAD_URL, formData = formData {
                        append("\"file\"", zipFile.readBytes(), Headers.build {
                            append(HttpHeaders.ContentType, "application/x-zip-compressed")
                            append(HttpHeaders.ContentDisposition, "filename=\"file.zip\"")
                        })
                    }) {
                        onUpload { bytesSentTotal, contentLength ->
                            if (uploadStatusFlow.value is LoadStatus.Loading) {
                                uploadStatusFlow.value =
                                    LoadStatus.Loading(bytesSentTotal / contentLength.toFloat())
                        }
                    }
                }
                if (response.headers["X_RPC_OK"] == "true") {
                    val policiesAsset = response.body<GithubPoliciesAsset>()
                    uploadStatusFlow.value = LoadStatus.Success(policiesAsset)
                    DbSet.snapshotDao.update(snapshot.copy(githubAssetId = policiesAsset.id))
                } else if (response.headers["X_RPC_OK"] == "false") {
                    uploadStatusFlow.value = LoadStatus.Failure(response.body<RpcError>())
                } else {
                    uploadStatusFlow.value = LoadStatus.Failure(Exception(response.bodyAsText()))
                }
            } catch (e: Exception) {
                uploadStatusFlow.value = LoadStatus.Failure(e)
            }
        }
    }
}