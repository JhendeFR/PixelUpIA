$vocabPath = "app/src/main/assets/vocab.json"
$mergesPath = "app/src/main/assets/merges.txt"
$outVocabJson = "app/src/main/assets/clip_vocab.json"
$outMergesTxt = "app/src/main/assets/clip_merges.txt"

Copy-Item $vocabPath $outVocabJson -Force

$lines = Get-Content $mergesPath -Encoding UTF8
$filtered = @()
foreach ($line in $lines) {
    $trimmed = $line.Trim()
    if ($trimmed -and -not $trimmed.StartsWith("#")) {
        $filtered += $trimmed
    }
}
[System.IO.File]::WriteAllLines($outMergesTxt, $filtered, [System.Text.Encoding]::UTF8)

Write-Host "clip_vocab.json size: $((Get-Item $outVocabJson).Length)"
Write-Host "clip_merges.txt size: $((Get-Item $outMergesTxt).Length)"
Write-Host "clip_merges.txt count: $($filtered.Count)"
