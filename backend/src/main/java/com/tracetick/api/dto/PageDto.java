package com.tracetick.api.dto;

import java.util.List;

public record PageDto<T>(List<T> items, int page, int size, long total) {
}
