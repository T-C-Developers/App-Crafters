
package com.example.quickconnect.ui.blogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.quickconnect.data.AppDatabase
import com.example.quickconnect.databinding.FragmentBlogsBinding

class BlogsFragment : Fragment() {
    private var _binding: FragmentBlogsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: BroadcastsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBlogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup RecyclerView
        adapter = BroadcastsAdapter(emptyList())
        binding.recyclerBroadcasts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@BlogsFragment.adapter
        }

        // Observe broadcasts
        AppDatabase.getInstance(requireContext())
            .broadcastMessageDAO()
            .getAll()
            .observe(viewLifecycleOwner) { list ->
                adapter.update(list)
            }

        // Show dialog on FAB click
        binding.fabAddBroadcast.setOnClickListener {
            BroadcastDialogFragment().show(childFragmentManager, "BroadcastDialog")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}