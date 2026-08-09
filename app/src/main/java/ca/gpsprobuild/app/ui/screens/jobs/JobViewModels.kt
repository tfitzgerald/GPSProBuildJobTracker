package ca.gpsprobuild.app.ui.screens.jobs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.gpsprobuild.app.data.local.entity.CustomerEntity
import ca.gpsprobuild.app.data.local.entity.JobEntity
import ca.gpsprobuild.app.data.local.entity.JobEventEntity
import ca.gpsprobuild.app.data.repository.CustomerRepository
import ca.gpsprobuild.app.data.repository.JobRepository
import ca.gpsprobuild.app.domain.model.JobStatus
import ca.gpsprobuild.app.domain.model.JobType
import ca.gpsprobuild.app.domain.model.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ---------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------

data class JobRow(
    val job: JobEntity,
    val customerName: String
)

data class JobListUiState(
    val query: String = "",
    val openOnly: Boolean = true,
    val rows: List<JobRow> = emptyList(),
    val loading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JobListViewModel @Inject constructor(
    private val jobRepository: JobRepository,
    customerRepository: CustomerRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val openOnly = MutableStateFlow(true)

    private val jobs = combine(query, openOnly) { q, open -> q to open }
        .flatMapLatest { (q, open) ->
            when {
                q.isNotBlank() -> jobRepository.search(q.trim())
                open -> jobRepository.observeOpen()
                else -> jobRepository.observeAll()
            }
        }

    val state: StateFlow<JobListUiState> = combine(
        query, openOnly, jobs, customerRepository.observeAll()
    ) { q, open, jobList, customers ->
        val namesById = customers.associate { it.id to it.displayName }
        JobListUiState(
            query = q,
            openOnly = open,
            rows = jobList.map { JobRow(it, namesById[it.customerId] ?: "Unknown customer") },
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobListUiState())

    fun setQuery(value: String) = query.update { value }
    fun setOpenOnly(value: Boolean) = openOnly.update { value }
}

// ---------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------

data class JobDetailUiState(
    val job: JobEntity? = null,
    val customer: CustomerEntity? = null,
    val events: List<JobEventEntity> = emptyList(),
    val loading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JobDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jobRepository: JobRepository,
    private val customerRepository: CustomerRepository
) : ViewModel() {

    private val jobId: Long = savedStateHandle.get<String>("jobId")?.toLongOrNull() ?: 0L

    private val jobFlow = jobRepository.observeById(jobId)

    private val customerFlow = jobFlow.flatMapLatest { job ->
        if (job == null) kotlinx.coroutines.flow.flowOf(null)
        else customerRepository.observeById(job.customerId)
    }

    val state: StateFlow<JobDetailUiState> = combine(
        jobFlow, customerFlow, jobRepository.observeEvents(jobId)
    ) { job, customer, events ->
        JobDetailUiState(job, customer, events, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobDetailUiState())

    fun changeStatus(status: JobStatus) {
        val job = state.value.job ?: return
        viewModelScope.launch { jobRepository.changeStatus(job, status) }
    }

    fun addNote(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { jobRepository.addNote(jobId, text.trim()) }
    }
}

// ---------------------------------------------------------------------------
// Create / edit
// ---------------------------------------------------------------------------

data class JobFormState(
    val id: Long = 0,
    val customerId: Long = 0,
    val customerName: String = "",
    val title: String = "",
    val jobType: JobType = JobType.KITCHEN,
    val status: JobStatus = JobStatus.LEAD,
    val priority: Priority = Priority.NORMAL,
    val scopeOfWork: String = "",
    val estimateAmount: String = "",
    val startDate: LocalDate? = null,
    val targetEndDate: LocalDate? = null,
    val permitRequired: Boolean = false,
    val notes: String = "",
    val customers: List<CustomerEntity> = emptyList(),
    val saving: Boolean = false,
    val savedJobId: Long? = null,
    val error: String? = null
) {
    val isNew: Boolean get() = id == 0L
    val canSave: Boolean get() = title.isNotBlank() && customerId != 0L && !saving
}

@HiltViewModel
class JobEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jobRepository: JobRepository,
    private val customerRepository: CustomerRepository
) : ViewModel() {

    private val jobId: Long = savedStateHandle.get<String>("jobId")?.toLongOrNull() ?: 0L
    private val presetCustomerId: Long =
        savedStateHandle.get<String>("customerId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(JobFormState())
    val state: StateFlow<JobFormState> = _state.asStateFlow()

    private var original: JobEntity? = null

    init {
        viewModelScope.launch {
            customerRepository.observeAll().collect { customers ->
                _state.update { it.copy(customers = customers) }
                // Resolve the display name once the list arrives.
                val id = _state.value.customerId
                if (id != 0L) {
                    val name = customers.firstOrNull { it.id == id }?.displayName.orEmpty()
                    _state.update { it.copy(customerName = name) }
                }
            }
        }

        viewModelScope.launch {
            if (jobId != 0L) {
                jobRepository.getById(jobId)?.let { job ->
                    original = job
                    _state.update {
                        it.copy(
                            id = job.id,
                            customerId = job.customerId,
                            title = job.title,
                            jobType = job.jobType,
                            status = job.status,
                            priority = job.priority,
                            scopeOfWork = job.scopeOfWork.orEmpty(),
                            estimateAmount = job.estimateAmountCents
                                ?.let { cents -> "%.2f".format(cents / 100.0) }.orEmpty(),
                            startDate = job.startDate,
                            targetEndDate = job.targetEndDate,
                            permitRequired = job.permitRequired,
                            notes = job.notes.orEmpty()
                        )
                    }
                }
            } else if (presetCustomerId != 0L) {
                _state.update { it.copy(customerId = presetCustomerId) }
            }
        }
    }

    fun update(transform: (JobFormState) -> JobFormState) =
        _state.update { transform(it).copy(error = null) }

    fun selectCustomer(customer: CustomerEntity) = _state.update {
        it.copy(customerId = customer.id, customerName = customer.displayName, error = null)
    }

    fun save() {
        val form = _state.value
        if (!form.canSave) return
        _state.update { it.copy(saving = true) }

        viewModelScope.launch {
            runCatching {
                val cents = ca.gpsprobuild.app.core.util.Money.parseToCents(form.estimateAmount)
                val base = original ?: JobEntity(
                    customerId = form.customerId,
                    jobNumber = "",
                    title = form.title
                )
                jobRepository.save(
                    base.copy(
                        id = form.id,
                        customerId = form.customerId,
                        title = form.title.trim(),
                        jobType = form.jobType,
                        status = form.status,
                        priority = form.priority,
                        scopeOfWork = form.scopeOfWork.trim().takeIf { it.isNotBlank() },
                        estimateAmountCents = cents,
                        startDate = form.startDate,
                        targetEndDate = form.targetEndDate,
                        permitRequired = form.permitRequired,
                        notes = form.notes.trim().takeIf { it.isNotBlank() }
                    )
                )
            }.onSuccess { savedId ->
                _state.update { it.copy(saving = false, savedJobId = savedId) }
            }.onFailure { error ->
                _state.update { it.copy(saving = false, error = "Could not save: ${error.message}") }
            }
        }
    }
}
