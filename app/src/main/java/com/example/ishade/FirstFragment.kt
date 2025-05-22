package com.example.ishade

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.ishade.databinding.FragmentFirstBinding

/**
 * Fragmento principal que actúa como pantalla de inicio.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Listener para el botón Modo Automático
        binding.buttonAutomatico.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_AutomaticFragment)
        }

        // Listener para el botón Modo Manual
        binding.buttonManual.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_ManualFragment)
        }

        // Listener para el botón Fijar Horario
        binding.buttonFijarHorario.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_ScheduleFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}