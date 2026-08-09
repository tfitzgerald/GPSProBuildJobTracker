package ca.gpsprobuild.app.ui.screens.customers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.gpsprobuild.app.data.local.entity.ContactEntity
import ca.gpsprobuild.app.data.local.entity.CustomerEntity
import ca.gpsprobuild.app.data.local.entity.JobEntity
import ca.gpsprobuild.app.data.repository.CustomerRepository
import ca.gpsprobuild.app.data.repository.JobRepository
import ca.gpsprobuild.app.domain.model.ContactMethod
import ca.gpsprobuild.app.domain.model.CustomerStatus
import ca.gpsprobuild.app.domain.model.CustomerType
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
import javax.inject.Inject

// ---------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------

enum class CustomerFilter(val label: String) {
    ALL("All"),
    LEADS("Leads"),
    ACTIVE("Active"),
    PAST("Past"),
    FAVOURITES("Favourites")
}

data class CustomerListUiState(
    val query: String = "",
    val filter: CustomerFilter = CustomerFilter.ALL,
    val customers: List<CustomerEntity> = emptyList(),
    val loading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomerListViewModel @Inject constructor(
    private val repository: CustomerRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(CustomerFilter.ALL)

    private val results = query.flatMapLatest { q ->
        if (q.isBlank()) repository.observeAll() else repository.search(q.trim())
    }

    val state: StateFlow<CustomerListUiState> = combine(
        query, filter, results
    ) { q, f, list ->
        CustomerListUiState(
            query = q,
            filter = f,
            customers = list.filter { customer ->
                when (f) {
                    CustomerFilter.ALL -> true
                    CustomerFilter.LEADS -> customer.status == CustomerStatus.LEAD
                    CustomerFilter.ACTIVE -> customer.status == CustomerStatus.ACTIVE
                    CustomerFilter.PAST -> customer.status == CustomerStatus.PAST
                    CustomerFilter.FAVOURITES -> customer.isFavourite
                }
            },
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomerListUiState())

    fun setQuery(value: String) = query.update { value }
    fun setFilter(value: CustomerFilter) = filter.update { value }
}

// ---------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------

data class CustomerDetailUiState(
    val customer: CustomerEntity? = null,
    val contacts: List<ContactEntity> = emptyList(),
    val jobs: List<JobEntity> = emptyList(),
    val loading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CustomerRepository,
    jobRepository: JobRepository
) : ViewModel() {

    private val customerId: Long = savedStateHandle.get<String>("customerId")?.toLongOrNull() ?: 0L

    val state: StateFlow<CustomerDetailUiState> = combine(
        repository.observeById(customerId),
        repository.observeContacts(customerId),
        jobRepository.observeForCustomer(customerId)
    ) { customer, contacts, jobs ->
        CustomerDetailUiState(customer, contacts, jobs, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomerDetailUiState())

    fun toggleFavourite() {
        val customer = state.value.customer ?: return
        viewModelScope.launch { repository.setFavourite(customer, !customer.isFavourite) }
    }

    fun archive(onDone: () -> Unit) {
        val customer = state.value.customer ?: return
        viewModelScope.launch {
            repository.archive(customer)
            onDone()
        }
    }
}

// ---------------------------------------------------------------------------
// Edit / create
// ---------------------------------------------------------------------------

data class CustomerFormState(
    val id: Long = 0,
    val displayName: String = "",
    val companyName: String = "",
    val customerType: CustomerType = CustomerType.RESIDENTIAL,
    val status: CustomerStatus = CustomerStatus.LEAD,
    val primaryPhone: String = "",
    val secondaryPhone: String = "",
    val email: String = "",
    val preferredContact: ContactMethod = ContactMethod.ANY,
    val street1: String = "",
    val street2: String = "",
    val city: String = "Pickering",
    val province: String = "ON",
    val postalCode: String = "",
    val referralSource: String = "",
    val gateCode: String = "",
    val accessNotes: String = "",
    val notes: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
) {
    val isNew: Boolean get() = id == 0L
    val canSave: Boolean get() = displayName.isNotBlank() && !saving
}

@HiltViewModel
class CustomerEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CustomerRepository
) : ViewModel() {

    private val customerId: Long = savedStateHandle.get<String>("customerId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(CustomerFormState())
    val state: StateFlow<CustomerFormState> = _state.asStateFlow()

    /** Retained so an edit preserves sync identity instead of minting a new one. */
    private var original: CustomerEntity? = null

    init {
        if (customerId != 0L) {
            viewModelScope.launch {
                repository.getById(customerId)?.let { customer ->
                    original = customer
                    _state.value = CustomerFormState(
                        id = customer.id,
                        displayName = customer.displayName,
                        companyName = customer.companyName.orEmpty(),
                        customerType = customer.customerType,
                        status = customer.status,
                        primaryPhone = customer.primaryPhone.orEmpty(),
                        secondaryPhone = customer.secondaryPhone.orEmpty(),
                        email = customer.email.orEmpty(),
                        preferredContact = customer.preferredContact,
                        street1 = customer.street1.orEmpty(),
                        street2 = customer.street2.orEmpty(),
                        city = customer.city.orEmpty(),
                        province = customer.province.orEmpty(),
                        postalCode = customer.postalCode.orEmpty(),
                        referralSource = customer.referralSource.orEmpty(),
                        gateCode = customer.gateCode.orEmpty(),
                        accessNotes = customer.accessNotes.orEmpty(),
                        notes = customer.notes.orEmpty()
                    )
                }
            }
        }
    }

    fun update(transform: (CustomerFormState) -> CustomerFormState) =
        _state.update { transform(it).copy(error = null) }

    fun save() {
        val form = _state.value
        if (!form.canSave) return
        _state.update { it.copy(saving = true) }

        viewModelScope.launch {
            runCatching {
                val base = original ?: CustomerEntity(displayName = form.displayName)
                repository.save(
                    base.copy(
                        id = form.id,
                        displayName = form.displayName.trim(),
                        companyName = form.companyName.trim().takeIf { it.isNotBlank() },
                        customerType = form.customerType,
                        status = form.status,
                        primaryPhone = form.primaryPhone.trim().takeIf { it.isNotBlank() },
                        secondaryPhone = form.secondaryPhone.trim().takeIf { it.isNotBlank() },
                        email = form.email.trim().takeIf { it.isNotBlank() },
                        preferredContact = form.preferredContact,
                        street1 = form.street1.trim().takeIf { it.isNotBlank() },
                        street2 = form.street2.trim().takeIf { it.isNotBlank() },
                        city = form.city.trim().takeIf { it.isNotBlank() },
                        province = form.province.trim().takeIf { it.isNotBlank() },
                        postalCode = form.postalCode.trim().takeIf { it.isNotBlank() },
                        referralSource = form.referralSource.trim().takeIf { it.isNotBlank() },
                        gateCode = form.gateCode.trim().takeIf { it.isNotBlank() },
                        accessNotes = form.accessNotes.trim().takeIf { it.isNotBlank() },
                        notes = form.notes.trim().takeIf { it.isNotBlank() }
                    )
                )
            }.onSuccess {
                _state.update { it.copy(saving = false, saved = true) }
            }.onFailure { error ->
                _state.update { it.copy(saving = false, error = "Could not save: ${error.message}") }
            }
        }
    }
}
