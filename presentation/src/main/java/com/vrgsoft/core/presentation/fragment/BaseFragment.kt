package com.vrgsoft.core.presentation.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.vrgsoft.core.presentation.common.LayoutResProcessor
import com.vrgsoft.core.presentation.router.BaseViewModelImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.jetbrains.annotations.TestOnly
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.KodeinTrigger
import org.kodein.di.android.closestKodein

abstract class BaseFragment<B : ViewDataBinding> : Fragment(), KodeinAware {

    //region fields

    private var test: Boolean = false

    private val mainJob = Job()

    private val defaultScope = CoroutineScope(Dispatchers.Main)
    private val fragmentScope = CoroutineScope(Dispatchers.IO + mainJob)

    protected val mainScope: CoroutineScope
        get() {
            return if (test) {
                defaultScope
            } else {
                fragmentScope
            }
        }

    private lateinit var _binding: B
    protected val binding: B
        get() = _binding

    private var diEnabled = true

    private val layoutResProcessor: LayoutResProcessor by lazy {
        LayoutResProcessor(
            context = requireContext(),
            superClass = this.javaClass.superclass,
            superClassGeneric = this.javaClass.genericSuperclass
        )
    }

    //endregion

    //region Kodein

    private val _parentKodein: Kodein by closestKodein()
    override val kodein: Kodein = Kodein.lazy {
        extend(_parentKodein, true)
        with(parentFragment) {
            if (this is BaseFragment<*>) {
                extend(kodein, true)
            }
        }
        import(viewModelModule, true)
        import(kodeinModule, true)
    }

    open val kodeinModule: Kodein.Module = Kodein.Module("default") {}

    open val viewModelModule: Kodein.Module = Kodein.Module("default2") {}

    override val kodeinTrigger = KodeinTrigger()

    abstract val viewModel: BaseViewModel

    //endregion

    //region lifecycle

    override fun onAttach(context: Context) {
        if (diEnabled) {
            kodeinTrigger.trigger()
        }
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = DataBindingUtil.inflate(inflater, getLayoutRes(), container, false)
        return _binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding.lifecycleOwner = this
        lifecycle.addObserver(viewModel)

        viewCreated(savedInstanceState)
    }

    override fun onStop() {
        mainJob.cancel()
        super.onStop()
    }

    //endregion

    //region abstracts

    abstract fun viewCreated(savedInstanceState: Bundle?)

    //endregion

    /**
     * Retrieves a resource from a fragment class,
     * You can override to specify a resource manually
     */
    @LayoutRes
    open fun getLayoutRes(): Int = layoutResProcessor.getLayoutRes()

    //region utils

    inline fun <reified VM : BaseViewModelImpl> vm(factory: ViewModelProvider.Factory): VM =
        ViewModelProviders.of(this, factory)[VM::class.java]

    //endregion

    //region test methods

    @TestOnly
    fun prepareForTest(disableDi: Boolean = false) {
        test = true
        diEnabled = !disableDi
    }

    //endregion
}
