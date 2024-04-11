/*
 * Copyright 2022-2024 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sleeper.core.record.serialiser;

import sleeper.core.record.ResultsBatch;

/**
 * Interface for serialising and deserialing results batches.
 */
public interface ResultsBatchSerialiser {

    /**
     * Serialise a results batch to a string.
     *
     * @param  resultsBatch the results batch
     * @return              a serialised string
     */
    String serialise(ResultsBatch resultsBatch);

    /**
     * Deserialise a string to a results batch.
     *
     * @param  serialisedResultsBatch the serialised results batch
     * @return                        a {@link ResultsBatch} object
     */
    ResultsBatch deserialise(String serialisedResultsBatch);
}
