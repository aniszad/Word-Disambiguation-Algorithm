### This was first Created on July 2023


# Word Sense Disambiguation Algorithm

A Java implementation of a Word Sense Disambiguation (WSD) algorithm that uses semantic similarity metrics and WordNet to disambiguate word meanings in text.

## Overview

This algorithm implements a context-based approach to WSD using:
- WordNet as the lexical database
- Leacock-Chodorow similarity measure
- Nearest neighbor disambiguation strategy
- Brown Corpus for evaluation

## Features

- Processes text input to identify ambiguous and unambiguous words
- Uses context windows (neighboring words) for disambiguation
- Implements recursive disambiguation for complex cases
- Evaluates results against SemCor-tagged data
- Generates detailed evaluation reports in DOCX format

## Dependencies

- ExtJWNL (Extended Java WordNet Library)
- Apache POI (for DOCX generation)
- RiTa (for text processing)
- WordNet database

## Algorithm Steps

1. Text Preprocessing
   - Tokenization
   - Lemmatization
   - POS tagging (focused on nouns)

2. Disambiguation Process
   - Identifies unambiguous words
   - Uses context windows for ambiguous words
   - Applies Leacock-Chodorow similarity
   - Recursively processes complex cases

3. Evaluation
   - Compares results with SemCor ground truth
   - Generates success/failure metrics
   - Creates detailed evaluation tables

## Usage

```java
// Create an instance with input sentence
DisambiguationAlgorithm algorithm = new DisambiguationAlgorithm(sentence, uneditedSentence);

// Run disambiguation
algorithm.mainProcedure();
```

## Output

The algorithm generates:
- Console output showing disambiguation decisions
- DOCX report with detailed evaluation tables
- Overall accuracy metrics

## Performance

The algorithm's performance is measured by:
- Accuracy per sentence
- Overall accuracy across the corpus
- Detailed success/failure analysis for each word

## Limitations

- Currently focuses on noun disambiguation only
- Requires pre-tagged corpus for evaluation
- Dependent on WordNet coverage

## Contributing

Feel free to contribute by:
- Adding support for other POS tags
- Implementing additional similarity measures
- Improving evaluation metrics
- Enhancing documentation