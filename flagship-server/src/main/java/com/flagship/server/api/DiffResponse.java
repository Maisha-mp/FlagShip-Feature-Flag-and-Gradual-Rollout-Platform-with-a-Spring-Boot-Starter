package com.flagship.server.api;

import com.flagship.server.change.DiffEntry;
import java.util.List;

public record DiffResponse(int from, int to, List<DiffEntry> changes) { }