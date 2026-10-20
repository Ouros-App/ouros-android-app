package com.ouros.app.feature.main.goals

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.ouros.app.R
import com.ouros.app.data.goals.GoalResponse

data class GoalListItem(
    val goal: GoalResponse,
    val isUpdating: Boolean
)

class GoalsAdapter(
    private val onToggle: (GoalResponse, Boolean) -> Unit,
    private val onTitleClick: ((GoalResponse) -> Unit)? = null
) : ListAdapter<GoalListItem, GoalsAdapter.GoalViewHolder>(DiffCallback) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long =
        getItem(position).goal.id

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): GoalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_goal, parent, false)

        return GoalViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: GoalViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    inner class GoalViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView =
            itemView.findViewById(R.id.cardGoal)

        private val content: View =
            itemView.findViewById(R.id.goalContent)

        private val title: TextView =
            itemView.findViewById(R.id.textGoalTitle)

        private val check: CheckBox =
            itemView.findViewById(R.id.checkGoal)

        fun bind(item: GoalListItem) {
            val goal = item.goal

            title.text = goal.title
            check.setOnCheckedChangeListener(null)
            check.isChecked = goal.isCompleted
            check.isEnabled = !item.isUpdating
            card.isEnabled = !item.isUpdating
            card.alpha = if (item.isUpdating) 0.6f else 1f

            content.setBackgroundResource(
                if (goal.isCompleted) {
                    R.drawable.bg_goal_completed
                } else {
                    R.drawable.bg_goal_pending
                }
            )

            check.setOnCheckedChangeListener { _, checked ->
                if (!item.isUpdating) {
                    onToggle(goal, checked)
                }
            }

            card.setOnClickListener {
                if (!item.isUpdating) {
                    check.performClick()
                }
            }

            title.setOnClickListener(
                if (onTitleClick == null) {
                    null
                } else {
                    View.OnClickListener {
                        if (!item.isUpdating) {
                            onTitleClick.invoke(goal)
                        }
                    }
                }
            )

            title.isClickable = onTitleClick != null
        }
    }

    private object DiffCallback :
        DiffUtil.ItemCallback<GoalListItem>() {

        override fun areItemsTheSame(
            oldItem: GoalListItem,
            newItem: GoalListItem
        ): Boolean = oldItem.goal.id == newItem.goal.id

        override fun areContentsTheSame(
            oldItem: GoalListItem,
            newItem: GoalListItem
        ): Boolean = oldItem == newItem
    }
}