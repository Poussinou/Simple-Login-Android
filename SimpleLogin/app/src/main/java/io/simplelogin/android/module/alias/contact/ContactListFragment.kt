package io.simplelogin.android.module.alias.contact

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.simplelogin.android.R
import io.simplelogin.android.databinding.FragmentContactListBinding
import io.simplelogin.android.module.home.HomeActivity
import io.simplelogin.android.utils.baseclass.BaseFragment
import io.simplelogin.android.utils.extension.*
import io.simplelogin.android.utils.model.Alias

class ContactListFragment : BaseFragment(), HomeActivity.OnBackPressed,
    Toolbar.OnMenuItemClickListener {
    private lateinit var binding: FragmentContactListBinding
    private lateinit var alias: Alias
    private lateinit var viewModel: ContactListViewModel
    private lateinit var adapter: ContactListAdapter
    private lateinit var howToBottomSheetBehavior: BottomSheetBehavior<View>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Binding
        binding = FragmentContactListBinding.inflate(layoutInflater)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.setOnMenuItemClickListener(this)
        alias = ContactListFragmentArgs.fromBundle(requireArguments()).alias

        binding.emailTextField.text = alias.email
        binding.emailTextField.isSelected = true // to trigger marquee animation

        // HowToBottomSheet
        val screenHeight = activity?.window?.decorView?.height
        screenHeight?.let {
            binding.howToBottomSheet.root.layoutParams.height = screenHeight * 90 / 100
        }

        howToBottomSheetBehavior = BottomSheetBehavior.from(binding.howToBottomSheet.root)
        howToBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        binding.howToBottomSheet.closeButton.setOnClickListener {
            howToBottomSheetBehavior.hide()
        }
        howToBottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                binding.dimView.alpha = slideOffset * 60 / 100
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> binding.dimView.visibility = View.GONE
                    else -> {
                        binding.dimView.visibility = View.VISIBLE
                        binding.dimView.setOnTouchListener { _, _ ->
                            // Must return true here to intercept touch event
                            // if not the event is passed to next listener which cause the whole root is clickable
                            true
                        }
                    }
                }
            }
        })

        // ViewModel
        val tempViewModel: ContactListViewModel by viewModels {
            ContactListViewModelFactory(
                context ?: throw IllegalStateException("Context is null"), alias
            )
        }
        viewModel = tempViewModel
        viewModel.fetchContacts()
        viewModel.eventHaveNewContacts.observe(viewLifecycleOwner, Observer { haveNewContacts ->
            activity?.runOnUiThread {
                if (haveNewContacts) {
                    adapter.submitList(viewModel.contacts)
                }

                if (binding.swipeRefreshLayout.isRefreshing) {
                    context?.toastUpToDate()
                    binding.swipeRefreshLayout.isRefreshing = false
                }

                updateUiBaseOnNumOfContacts()
            }
        })

        viewModel.error.observe(viewLifecycleOwner, Observer { error ->
            if (error != null) {
                context?.toastError(error)
                viewModel.onHandleErrorComplete()
                binding.swipeRefreshLayout.isRefreshing = false
            }
        })

        // RecyclerView
        adapter = ContactListAdapter()
        binding.recyclerView.adapter = adapter
        val linearLayoutManager = LinearLayoutManager(context)
        binding.recyclerView.layoutManager = linearLayoutManager

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if ((linearLayoutManager.findLastCompletelyVisibleItemPosition() == viewModel.contacts.size - 1) && viewModel.moreToLoad) {
                    viewModel.fetchContacts()
                }
            }
        })

        binding.swipeRefreshLayout.setOnRefreshListener { viewModel.refreshContacts() }
        setLoading(false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // On configuration change, force trigger refresh recyclerView
        if (adapter.itemCount == 0 && viewModel.contacts.isNotEmpty()) {
            adapter.submitList(viewModel.contacts)
            updateUiBaseOnNumOfContacts()
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.rootConstraintLayout.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun updateUiBaseOnNumOfContacts() {
        if (viewModel.contacts.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.icebergImageView.visibility = View.VISIBLE
            binding.instructionTextView.visibility = View.VISIBLE
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.icebergImageView.visibility = View.GONE
            binding.instructionTextView.visibility = View.GONE
        }
    }

    // HomeActivity.OnBackPressed
    override fun onBackPressed() {
        if (howToBottomSheetBehavior.isExpanded()) {
            howToBottomSheetBehavior.hide()
        } else {
            findNavController().navigateUp()
        }
    }

    // Toolbar.OnMenuItemClickListener
    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.addMenuItem -> Unit
            R.id.howToMenuItem -> howToBottomSheetBehavior.expand()
        }

        return true
    }
}