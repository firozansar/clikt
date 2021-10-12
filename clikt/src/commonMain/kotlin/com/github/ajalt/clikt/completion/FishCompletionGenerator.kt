package com.github.ajalt.clikt.completion

import com.github.ajalt.clikt.completion.CompletionCandidates.Custom.ShellType
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.Option
import com.github.ajalt.clikt.parameters.options.OptionWithValues

internal object FishCompletionGenerator {
    fun generateFishCompletion(command: CliktCommand): String {
        if (!command.hasFishCompletionRequirements) return ""
        return generateFishCompletionForCommand(command)
    }


    private fun generateFishCompletionForCommand(command: CliktCommand): String = buildString {
        val parentCommandName = command.currentContext.parentNames().lastOrNull()
        val rootCommandName = command.currentContext.commandNameWithParents().first()
        val isTopLevel = parentCommandName == null
        val commandName = command.commandName
        val options = command._options.filterNot { it.hidden }
        val arguments = command._arguments
        val subcommands = command._subcommands
        val hasSubcommands = subcommands.isNotEmpty()
        val subcommandsVarName = command.currentContext.commandNameWithParents().subcommandsVarName()
        val parentSubcommandsVarName = when {
            isTopLevel -> subcommandsVarName
            else -> command.currentContext.parentNames().subcommandsVarName()
        }

        if (isTopLevel) {
            appendLine("""
                |# Command completion for $commandName
                |# Generated by Clikt
            """.trimMargin())
        }

        if (hasSubcommands || !isTopLevel) {
            appendLine("\n\n### Setup for $commandName")
        }

        if (hasSubcommands) {
            val subcommandsStr = subcommands.joinToString(" ") { it.commandName }
            appendLine("set -l $subcommandsVarName '$subcommandsStr'")
        }

        if (!isTopLevel) {
            append("complete -c $rootCommandName -f ")

            if (rootCommandName == parentCommandName) {
                append("-n __fish_use_subcommand ")
            } else {
                append("-n \"__fish_seen_subcommand_from $parentCommandName; and not __fish_seen_subcommand_from \$$parentSubcommandsVarName\" ")
            }

            append("-a $commandName ")

            val help = command.commandHelp.replace("'", "\\'")
            if (help.isNotBlank()) {
                append("-d '${help}'")
            }

            appendLine()
        }

        if (options.any { o -> o.allNames.any { it.isValidFishCompletionOption } }) {
            appendLine("\n## Options for $commandName")
        }

        for (option in options) {
            val names = option.allNames.filter { it.isValidFishCompletionOption }
            if (names.isEmpty()) {
                continue
            }

            appendCompleteCall(rootCommandName, isTopLevel, hasSubcommands, commandName)

            for (name in names) {
                append(' ')
                when {
                    name.startsWith("--") -> append("-l ")
                    name.length == 2 -> append("-s ")
                    else -> append("-o ")
                }
                append(name.trimStart('-'))
            }

            if (option.nvalues.first > 0) {
                append(" -r")
            }

            appendParamCompletion(option.completionCandidates)
            appendHelp(option.optionHelp)
            appendLine()
        }

        if (arguments.isNotEmpty()) {
            appendLine("\n## Arguments for $commandName")
        }

        for (argument in arguments) {
            appendCompleteCall(rootCommandName, isTopLevel, hasSubcommands, commandName)
            appendParamCompletion(argument.completionCandidates)
            appendHelp(argument.argumentHelp)
            appendLine()
        }

        for (subcommand in subcommands) {
            append(generateFishCompletionForCommand(
                command = subcommand
            ))
        }
    }

    private fun StringBuilder.appendCompleteCall(
        rootCommandName: String,
        isTopLevel: Boolean,
        hasSubcommands: Boolean,
        commandName: String,
    ) {
        append("complete -c $rootCommandName")

        if (isTopLevel) {
            if (hasSubcommands) {
                append(" -n \"not __fish_seen_subcommand_from \$${commandName}_subcommands\"")
            }
        } else {
            append(" -n \"__fish_seen_subcommand_from $commandName\"")
        }
    }

    private fun StringBuilder.appendHelp(help: String) {
        val h = help.takeWhile { it !in "\r\n" }.replace("'", "\\'")
        if (h.isNotBlank()) {
            append(" -d '$h'")
        }
    }

    private fun StringBuilder.appendParamCompletion(completion: CompletionCandidates) {
        when (completion) {
            is CompletionCandidates.None -> {
            }
            is CompletionCandidates.Path -> {
                append(" -F")
            }
            is CompletionCandidates.Hostname -> {
                append(" -fa \"(__fish_print_hostnames)\"")
            }
            is CompletionCandidates.Username -> {
                append(" -fa \"(__fish_complete_users)\"")
            }
            is CompletionCandidates.Fixed -> {
                completion.candidates.joinTo(this, " ", prefix = " -fa \"", postfix = "\"")
            }
            is CompletionCandidates.Custom -> {
                val customCompletion = completion.generator(ShellType.FISH)
                append(" -fa $customCompletion")
            }
        }
    }

    private fun List<String>.subcommandsVarName(): String {
        return joinToString("_", postfix = "_subcommands") { it.replace(Regex("\\W"), "_") }
    }

    private val CliktCommand.hasFishCompletionRequirements: Boolean
        get() = _arguments.isNotEmpty()
                || _subcommands.isNotEmpty()
                || _options.flatMap { it.allNames }.any { it.isValidFishCompletionOption }

    private val String.isValidFishCompletionOption: Boolean
        get() = startsWith('-')

    private val Option.allNames get() = names + secondaryNames
}
