// *** core/model/StoreItemModel.kt *** //
package by.quty.launch.core.model

import by.quty.launch.core.managers.Shell
import by.quty.launch.core.utilities.UpdateHelper

/**
 * Объединённая модель элемента магазина (remote + local).
 */
data class StoreItemModel(
    val name: String,
    val type: StoreItemTypeModel,

    val displayName: String,
    val author: String?,
    val version: String?,
    val previewUrl: String?,
    val previewBase64: String?,

    val fileSize: String?,
    val datePublished: String?,
    val minQutyLaunchVersion: String?,
    val orientation: String?,
    val repoUrl: String?,
    val telegramUrl: String?,

    val isInstalled: Boolean,
    val installedVersion: String?,
    val isActive: Boolean,
    val activeVersion: String?,

    /** true, если оболочка загружена из-за активного Safe Mode (Default), а не выбрана пользователем. */
    val isActiveInSafeMode: Boolean = false,

    val remoteInfo: StoreRemoteItemModel?,
    val localShell: Shell?
) {

    /** true, если на сервере версия новее локальной. */
    val updateAvailable: Boolean
        get() {
            if (!isInstalled) return false
            val remoteVersion = remoteInfo?.version ?: return false
            val localVersion = installedVersion ?: return false
            return UpdateHelper.isNewerVersion(remoteVersion, localVersion)
        }

    /** true, если локальная версия новее серверной. */
    val installedIsNewer: Boolean
        get() {
            if (!isInstalled) return false
            val remoteVersion = remoteInfo?.version ?: return false
            val localVersion = installedVersion ?: return false
            return UpdateHelper.isNewerVersion(localVersion, remoteVersion)
        }

    /** true, если активна и activeVersion совпадает с installedVersion (или неизвестна). */
    val isActiveSameVersion: Boolean
        get() {
            if (!isActive) return false
            val localVersion = installedVersion ?: return true
            return activeVersion == null || activeVersion == localVersion
        }

    /** Элемент есть только локально. */
    val isLocalOnly: Boolean
        get() = remoteInfo == null

    /** Можно установить: элемент есть в каталоге и ещё не установлен. */
    val canInstall: Boolean
        get() = remoteInfo != null && !isInstalled

    /**
     * Можно обновить: элемент установлен, а версия локального файла неизвестна
     * (манифест без version) либо на сервере она новее.
     *
     * Взаимоисключающе с canInstall: у установленного элемента не бывает
     * одновременно «Установить» и «Обновить» — оба пункта вели в один и тот же
     * installItem(), но выглядели как разные действия.
     */
    val canUpdate: Boolean
        get() = isInstalled &&
                remoteInfo?.version != null &&
                (installedVersion.isNullOrEmpty() || updateAvailable)

    /** Можно применить (установлено и не активно). */
    val canApply: Boolean
        get() = isInstalled && !isActive

    /** Есть локальный файл (проверку URI делает фрагмент). */
    val canShare: Boolean
        get() = localShell != null
}