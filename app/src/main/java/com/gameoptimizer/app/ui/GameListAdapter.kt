package com.gameoptimizer.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gameoptimizer.app.R
import com.gameoptimizer.app.optimizer.InstalledGame

class GameListAdapter(
    private val games: List<InstalledGame>,
    private val onGameClick: (InstalledGame) -> Unit
) : RecyclerView.Adapter<GameListAdapter.GameViewHolder>() {

    inner class GameViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivGameIcon)
        val label: TextView = view.findViewById(R.id.tvGameLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game, parent, false)
        return GameViewHolder(view)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        val game = games[position]
        holder.label.text = game.label
        if (game.icon != null) {
            holder.icon.setImageDrawable(game.icon)
        }
        holder.itemView.setOnClickListener { onGameClick(game) }
    }

    override fun getItemCount(): Int = games.size
}
