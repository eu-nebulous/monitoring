/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.nebulous.ems.translate.NebulousEmsTranslatorProperties;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okio.Path;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexService implements InitializingBean {
	private final Collection<String> FILES_TO_EXCLUDE_FROM_PURGE = new LinkedHashSet<>(Set.of("index.json", "empty.yml"));

	private final NebulousEmsTranslatorProperties translatorProperties;
	private final ApplicationContext applicationContext;
	private final EmsBootProperties properties;
	private final ObjectMapper objectMapper;
	private final Object LOCK = new Object();

	@Override
	public void afterPropertiesSet() throws Exception {
		if (translatorProperties!=null && StringUtils.isNotBlank(translatorProperties.getExtensionModel())) {
			FILES_TO_EXCLUDE_FROM_PURGE.add(translatorProperties.getExtensionModel());
		}
		log.debug("IndexService: FILES_TO_EXCLUDE_FROM_PURGE: {}", FILES_TO_EXCLUDE_FROM_PURGE);
	}

	public void initIndexFile() throws IOException {
		try {
			// Load index file contents (if it exists)
			@NonNull Map<String, Object> indexContents = loadIndexContents();
			if (! indexContents.isEmpty()) {
				log.warn("Index file is not empty. Will not initialize it.");
				return;
			}
		} catch (IOException e) {
			log.debug("Initializing index file: ", e);
		}

		// Initialize index file
		storeIndexContents(Map.of());
		log.info("Index file initialized");
	}

	void storeToIndex(String appId, Map<String,String> values) throws IOException {
		log.debug("storeToIndex: BEGIN: app-id={}, values={}", appId, values);
		synchronized (LOCK) {
			// Load index file contents
			Map<String, Object> indexContents = loadIndexContents();

			// Create or update entry
			Map<String,String> entry = (Map<String, String>) indexContents.computeIfAbsent(appId, k -> new HashMap<>());
			values.forEach((key, val) -> {
				if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(val))
					entry.put(key, val);
			});
			entry.put("creation-ts", Instant.now().toString());

			// Store index contents back to file
			storeIndexContents(indexContents);
		}
	}

	Map<String,String> getFromIndex(String appId) throws IOException {
		log.debug("getFromIndex: BEGIN: app-id={}", appId);

		// Load info from models store
		Map<String, Object> indexContents = loadIndexContents();

		// Find the entry for 'appId'
		Object entry = indexContents.entrySet().stream()
				.filter(e -> e.getKey().equalsIgnoreCase(appId))
				.map(Map.Entry::getValue)
				.findFirst().orElse(null);
		log.debug("getFromIndex: END: app-id={}, result={}", appId, entry);

		return (Map) entry;
	}

	@NonNull
	private Map<String, Object> loadIndexContents() throws IOException {
		Map<String,Object> indexContents = null;
		if (Path.get(properties.getModelsIndexFile()).toFile().exists()) {
			try (FileReader reader = new FileReader(properties.getModelsIndexFile())) {
				indexContents = objectMapper.readValue(reader, Map.class);
			}
		}
		log.debug("Model Index contents loaded: {}", indexContents);
		if (indexContents==null)
			indexContents = new LinkedHashMap<>();
		return indexContents;
	}

	private void storeIndexContents(Map<String, Object> indexContents) throws IOException {
		log.debug("Model Index contents to write: {}", indexContents);
		if (indexContents==null) indexContents = Map.of();
		try (FileWriter writer = new FileWriter(properties.getModelsIndexFile())) {
			objectMapper.writeValue(writer, indexContents);
		}
	}

	private Map<String,Map<String,String>> castToMapMap(Map<String,Object> map) {
		return map.entrySet().stream().collect(Collectors.toMap(
				Map.Entry::getKey, e->(Map<String,String>) e.getValue()
		));
	}

	// ------------------------------  Public API  ------------------------------
	public Map<String,Map<String,String>> getAll() throws IOException {
		return castToMapMap( loadIndexContents() );
	}

	public Set<String> getAppIds() throws IOException {
		return loadIndexContents().keySet();
	}

	public Map<String, String> getAppData(@NonNull String appId) throws IOException {
		return (Map<String,String>) loadIndexContents().get(appId);
	}

	public String getAppMetricModel(@NonNull String appId) throws IOException {
		String fileName = getAppData(appId).get(ModelsService.MODEL_FILE_KEY);
		return applicationContext.getBean(ModelsService.class).readFromFile(fileName);
	}

	public Map<String,String> getAppBindings(@NonNull String appId) throws IOException {
		String fileName = getAppData(appId).get(ModelsService.BINDINGS_FILE_KEY);
		String bindingsStr = applicationContext.getBean(ModelsService.class).readFromFile(fileName);
		return objectMapper.readValue(bindingsStr, Map.class);
	}

	public synchronized boolean deleteAppData(@NonNull String appId) throws IOException {
		@NonNull Map<String, Object> map = loadIndexContents();
		boolean removed = map.remove(appId) != null;
		storeIndexContents(map);
		return removed;
	}

	public synchronized boolean deleteAll() throws IOException {
		return deleteAll(false);
	}

	public synchronized boolean deleteAll(boolean deleteFiles) throws IOException {
		log.info("Purging EMS Boot cache...");
		if (deleteFiles) {
			// Delete all files in models dir. (folders are excluded)
			log.trace("Models Dir: {}", properties.getModelsDir());
			File[] files = Path.get(properties.getModelsDir()).toFile().listFiles();
			if (files!=null) {
				Arrays.stream(files).filter(File::isFile)
						.filter(f -> ! FILES_TO_EXCLUDE_FROM_PURGE.contains(f.getName()))
						.peek(f -> log.warn("  Deleting file: {}", f))
						.forEach(File::delete);
			}
		}

		// Initialize index file
		storeIndexContents(Map.of());
		log.info("Index file initialized");
		return true;
	}

	public synchronized boolean deleteUnused() throws IOException {
		log.info("Purging unused files from EMS Boot cache...");

		// Find files in use
		Map<String, Map<String, String>> map = getAll();
		Set<String> filesInUse = map.values().stream().flatMap(m -> m.values().stream()).collect(Collectors.toSet());
		filesInUse.addAll(FILES_TO_EXCLUDE_FROM_PURGE);
		log.debug("Files in use: {}", filesInUse);

		// Delete unused files in models dir. (folders are excluded)
		log.trace("Models Dir: {}", properties.getModelsDir());
		File[] files = Path.get(properties.getModelsDir()).toFile().listFiles();
		AtomicInteger deletedCnt = new AtomicInteger(0);
		if (files!=null) {
			Arrays.stream(files).filter(File::isFile)
					.filter(f -> ! filesInUse.contains(f.getName()))
					.peek(f -> log.warn("  Deleting file: {}", f))
					.forEach(f -> {
						if (f.delete()) deletedCnt.incrementAndGet();
					});
		}

		log.info("{} unused files removed from EMS Boot cache", deletedCnt);
		return true;
	}

	public synchronized boolean deleteAppsBefore(Instant before) throws IOException {
		return deleteAppsBefore(before, false);
	}

	public synchronized boolean deleteAppsBefore(Instant before, boolean deleteFiles) throws IOException {
		log.info("Purging apps from EMS Boot cache, older than {}...", before);

		// Find apps older than 'before'
		Map<String, Map<String, String>> map = getAll();
		Map<String, Map<String, String>> appsToDelete = map.entrySet().stream()
				.filter(e -> StringUtils.isNotBlank(e.getValue().get("creation-ts")))
				.filter(e -> Instant.parse(e.getValue().get("creation-ts")).isBefore(before))
				.collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue
				));
		log.debug("Apps to delete: {}", appsToDelete.keySet());

		// Delete appsToDelete
		AtomicInteger deletedCnt = new AtomicInteger(0);
		for (String id : appsToDelete.keySet()) {
			log.warn("  Deleting app: {}", id);
			deleteAppData(id);
			deletedCnt.incrementAndGet();
		}
		log.info("{} apps removed from EMS Boot cache", deletedCnt);

		// Also delete the files of the deleted apps
		if (deleteFiles) {
			Set<String> fileToDelete = appsToDelete.values().stream().flatMap(v -> v.values().stream()).collect(Collectors.toSet());
			log.debug("Files to delete: {}", fileToDelete);

			// Delete remaining files in models dir. (folders are excluded)
			log.trace("Models Dir: {}", properties.getModelsDir());
			File[] files = Path.get(properties.getModelsDir()).toFile().listFiles();
			deletedCnt.set(0);
			if (files != null) {
				Arrays.stream(files).filter(File::isFile)
						.filter(f -> fileToDelete.contains(f.getName()))
						.peek(f -> log.warn("  Deleting file: {}", f))
						.forEach(f -> {
							if (f.delete()) deletedCnt.incrementAndGet();
						});
			}

			log.info("{} files removed from EMS Boot cache", deletedCnt);
		}
		return true;
	}

	public synchronized void addAppData(String appId) throws IOException {
		long ts = Instant.now().toEpochMilli();
		String modelStr, bindingsStr, metricsStr;
		Map<String, String> data = Map.of(
				"model-file", modelStr = appId + "--model--" + ts + ".yml",
				"bindings-file", bindingsStr = appId + "--bindings--" + ts + ".json",
				"optimiser-metrics-file", metricsStr = appId + "--metrics--" + ts + ".json"
		);
		Files.writeString(Paths.get(properties.getModelsDir(), modelStr), "{}");
		Files.writeString(Paths.get(properties.getModelsDir(), bindingsStr), "{}");
		Files.writeString(Paths.get(properties.getModelsDir(), metricsStr), "[]");
		storeToIndex(appId, data);
	}
}